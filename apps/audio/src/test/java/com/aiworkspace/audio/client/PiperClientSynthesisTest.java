package com.aiworkspace.audio.client;

import com.aiworkspace.audio.config.AudioConfig;
import com.aiworkspace.audio.config.AudioSynthesisProperties;
import com.aiworkspace.audio.exceptions.AudioSynthesisBusyException;
import com.aiworkspace.audio.services.AudioValidator;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class PiperClientSynthesisTest {

    private final ScheduledExecutorService scheduler = new AudioConfig().piperDeadlineScheduler();

    @AfterEach
    void closeScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void rejectsOversizedInputBeforeOpeningAConnection() {
        var properties = AudioSynthesisProperties.builder().maxTextCharacters(3).build();
        var client = createClient("localhost", 1, properties);

        assertThrows(IllegalArgumentException.class, () -> client.synthesize("abcd"));
    }

    @Test
    void acceptsTheExactAudioByteLimitAndRejectsTheNextChunkBeforeReadingIt() throws Exception {
        var properties = AudioSynthesisProperties.builder().maxAudioSize(DataSize.ofBytes(8)).build();
        byte[] accepted = concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[8]), stopEvent());
        try (var server = respondWith(accepted)) {
            byte[] wav = createClient(server.host(), server.port(), properties).synthesize("Hello").wavContent();
            assertEquals(8, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(40));
            server.awaitCompletion();
        }
        byte[] oversized = concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[6]),
                "{\"type\":\"audio-chunk\",\"payload_length\":4}\n".getBytes(StandardCharsets.UTF_8));

        assertProviderFailure(properties, oversized, "Piper exceeded the synthesized audio size or duration limit");
    }

    @Test
    void enforcesDurationUsingTheProvidersSampleRate() throws Exception {
        var properties = AudioSynthesisProperties.builder().maxAudioDuration(Duration.ofMillis(1)).build();
        // 8 kHz, 16-bit mono permits 8 frames (16 bytes) in one millisecond.
        try (var server = respondWith(concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[16]), stopEvent()))) {
            assertEquals("speech.wav", createClient(server.host(), server.port(), properties).synthesize("Hello").filename());
            server.awaitCompletion();
        }
        assertProviderFailure(properties,
                concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[18])),
                "Piper exceeded the synthesized audio size or duration limit");
    }

    @Test
    void limitsUnknownEventsAndPreventsAudioStartFromResettingTheBudget() throws Exception {
        assertProviderFailure(AudioSynthesisProperties.builder().maxEvents(2).build(),
                concatenate(startEvent(8000), event("future-event", "{}", new byte[0]), stopEvent()),
                "Piper exceeded the synthesis event limit");
        assertProviderFailure(AudioSynthesisProperties.builder().build(),
                concatenate(startEvent(8000), startEvent(8000)), "Piper returned multiple audio-start events");
    }

    @Test
    void rejectsAudioThatArrivesBeforeItsFormat() throws Exception {
        assertProviderFailure(AudioSynthesisProperties.builder().build(), event("audio-chunk", "{}", new byte[2]),
                "Piper returned audio before audio format metadata");
    }

    @Test
    void rejectsConcurrentRequestsWithoutQueuingAndReleasesCapacityAfterSuccess() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        byte[] response = concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[2]), stopEvent());
        try (var server = new PiperTestServer(2, (socket, index) -> {
            if (index == 0) {
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("Test did not release the response");
                }
            }
            socket.getOutputStream().write(response);
        }); var requests = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = createClient(server.host(), server.port(), AudioSynthesisProperties.builder().build());
            var first = requests.submit(() -> client.synthesize("Hello"));
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertThrows(AudioSynthesisBusyException.class, () -> client.synthesize("Hello"));
            } finally {
                release.countDown();
            }
            assertEquals("speech.wav", first.get(3, TimeUnit.SECONDS).filename());
            assertEquals("speech.wav", client.synthesize("Hello").filename());
            server.awaitCompletion();
        }
    }

    @Test
    void releasesCapacityAfterMalformedResponses() throws Exception {
        byte[] response = concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[2]), stopEvent());
        try (var server = new PiperTestServer(2, (socket, index) -> socket.getOutputStream()
                .write(index == 0 ? "{}\n".getBytes(StandardCharsets.UTF_8) : response))) {
            var client = createClient(server.host(), server.port(), AudioSynthesisProperties.builder().build());

            assertThrows(UpstreamServiceException.class, () -> client.synthesize("Hello"));
            assertEquals("speech.wav", client.synthesize("Hello").filename());
            server.awaitCompletion();
        }
    }

    @Test
    void readTimeoutClosesAnInactiveProviderConnection() throws Exception {
        try (var server = new PiperTestServer(1, (socket, index) -> assertEquals(-1, socket.getInputStream().read()))) {
            var properties = AudioSynthesisProperties.builder()
                    .requestTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofMillis(100)).build();
            var client = createClient(server.host(), server.port(), properties);

            var exception = assertThrows(UpstreamServiceException.class, () -> client.synthesize("Hello"));

            assertInstanceOf(SocketTimeoutException.class, exception.getCause());
            server.awaitCompletion();
        }
    }

    @Test
    void totalDeadlineStopsContinuousProgressAndReleasesCapacity() throws Exception {
        var writes = new AtomicInteger();
        byte[] response = concatenate(startEvent(8000), event("audio-chunk", "{}", new byte[2]), stopEvent());
        try (var server = new PiperTestServer(2, (socket, index) -> {
            if (index == 0) {
                for (int count = 0; count < 200; count++) {
                    try {
                        socket.getOutputStream().write(' ');
                        socket.getOutputStream().flush();
                    } catch (IOException connectionClosed) {
                        return;
                    }
                    writes.incrementAndGet();
                    Thread.sleep(10);
                }
                throw new IOException("Client failed to close the socket at its deadline");
            }
            socket.getOutputStream().write(response);
        })) {
            var properties = AudioSynthesisProperties.builder()
                    .requestTimeout(Duration.ofMillis(500)).readTimeout(Duration.ofSeconds(2)).build();
            var client = createClient(server.host(), server.port(), properties);

            var exception = assertThrows(UpstreamServiceException.class, () -> client.synthesize("Hello"));
            assertEquals("Piper synthesis exceeded the total request deadline", exception.getMessage());
            assertTrue(writes.get() > 1);
            assertEquals("speech.wav", client.synthesize("Hello").filename());
            server.awaitCompletion();
        }
    }

    private PiperClient createClient(String host, int port, AudioSynthesisProperties properties) {
        return new PiperClient(host, port, new ObjectMapper(), properties, new AudioValidator(properties), scheduler);
    }

    private void assertProviderFailure(AudioSynthesisProperties properties, byte[] response, String message) throws Exception {
        try (var server = respondWith(response)) {
            var client = createClient(server.host(), server.port(), properties);

            var exception = assertThrows(UpstreamServiceException.class, () -> client.synthesize("Hello"));

            assertEquals("Piper", exception.serviceName());
            assertEquals(message, exception.getMessage());
            server.awaitCompletion();
        }
    }

    private PiperTestServer respondWith(byte[] response) throws IOException {
        return new PiperTestServer(1, (socket, index) -> socket.getOutputStream().write(response));
    }

    private byte[] startEvent(int rate) throws IOException {
        return event("audio-start", "{\"rate\":" + rate + ",\"width\":2,\"channels\":1}", new byte[0]);
    }

    private byte[] stopEvent() throws IOException {
        return event("audio-stop", "{}", new byte[0]);
    }

    private byte[] event(String type, String data, byte[] payload) throws IOException {
        byte[] metadata = data.getBytes(StandardCharsets.UTF_8);
        String header = "{\"type\":\"%s\",\"data_length\":%d,\"payload_length\":%d}\n"
                .formatted(type, metadata.length, payload.length);
        return concatenate(header.getBytes(StandardCharsets.UTF_8), metadata, payload);
    }

    private byte[] concatenate(byte[]... parts) throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            bytes.write(part);
        }
        return bytes.toByteArray();
    }
}
