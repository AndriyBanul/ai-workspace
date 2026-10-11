package com.aiworkspace.audio.client;

import com.aiworkspace.audio.config.AudioConfig;
import com.aiworkspace.audio.config.AudioSynthesisProperties;
import com.aiworkspace.audio.models.PcmAudioFormat;
import com.aiworkspace.audio.services.AudioValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiperClientTest {

    private final AudioSynthesisProperties properties = AudioSynthesisProperties.builder().build();
    private final ScheduledExecutorService scheduler = new AudioConfig().piperDeadlineScheduler();
    private final PiperClient client = new PiperClient("localhost", 10200, new ObjectMapper(),
            properties, new AudioValidator(properties), scheduler);

    @AfterEach
    void closeScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void rejectsOversizedHeadersAndDeclaredLengthsBeforeReadingBodies() throws Exception {
        var limitedProperties = AudioSynthesisProperties.builder()
                .maxHeaderSize(DataSize.ofBytes(128))
                .maxEventDataSize(DataSize.ofBytes(16))
                .maxEventPayloadSize(DataSize.ofBytes(4))
                .build();
        var limitedClient = new PiperClient("localhost", 10200, new ObjectMapper(),
                limitedProperties, new AudioValidator(limitedProperties), scheduler);
        for (String header : List.of(
                "x".repeat(129),
                "{\"type\":\"error\",\"data\":{\"text\":\"" + "x".repeat(129) + "\"}}\n",
                "{\"type\":\"future-event\",\"data_length\":17}\n",
                "{\"type\":\"audio-chunk\",\"data_length\":16,\"payload_length\":5}\n",
                "{\"type\":\"audio-chunk\",\"payload_length\":2147483647}\n"
        )) {
            var exception = assertThrows(IOException.class,
                    () -> limitedClient.readEvent(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8))));
            assertTrue(exception.getMessage().contains("size limit"));
        }
        var event = limitedClient.readEvent(new ByteArrayInputStream(buildEvent("audio-chunk", "{}", new byte[4])));
        assertEquals(4, event.payload().length);
        String exactHeader = "{\"type\":\"audio-stop\"}";
        exactHeader += " ".repeat(128 - exactHeader.length()) + "\n";
        assertEquals("audio-stop", limitedClient.readEvent(
                new ByteArrayInputStream(exactHeader.getBytes(StandardCharsets.UTF_8))).type());
    }

    @Test
    void readsInlineMetadataWithoutAdditionalDataAndPreservesBinaryFraming() throws Exception {
        byte[] pcm = {0, 127, (byte) 255, '\n'};
        var stream = new ByteArrayOutputStream();
        stream.write(buildEvent("audio-start", "{\"rate\":22050,\"width\":2,\"channels\":1,\"extra\":true}", null, new byte[0]));
        stream.write(buildEvent("audio-chunk", "{\"rate\":22050,\"width\":2,\"channels\":1}", null, pcm));
        stream.write(buildEvent("audio-stop", "{\"timestamp\":100}", null, new byte[0]));
        var input = new ByteArrayInputStream(stream.toByteArray());

        assertEquals(new PcmAudioFormat(22050, 2, 1), client.readEvent(input).audioFormat());
        assertArrayEquals(pcm, client.readEvent(input).payload());
        assertEquals("audio-stop", client.readEvent(input).type());
        assertEquals(0, input.available());
    }

    @Test
    void mergesAdditionalMetadataOverInlineValuesBeforeValidatingAudioFormat() throws Exception {
        var input = new ByteArrayInputStream(buildEvent("audio-start",
                "{\"rate\":22050.5,\"width\":2,\"extra\":{\"nested\":true}}",
                "{\"rate\":16000,\"channels\":2}", new byte[0]));

        assertEquals(new PcmAudioFormat(16000, 2, 2), client.readEvent(input).audioFormat());
        assertEquals(0, input.available());
    }

    @Test
    void readsInlineErrorsAndOverridesOnlyKeysInAdditionalMetadata() throws Exception {
        var inlineInput = new ByteArrayInputStream(buildEvent("error",
                "{\"text\":\"Voice unavailable — retry\",\"code\":\"voice_missing\"}", null, new byte[0]));
        var inlineError = client.readEvent(inlineInput).error();
        assertEquals("Voice unavailable — retry", inlineError.text());
        assertEquals("voice_missing", inlineError.code());

        var mixedInput = new ByteArrayInputStream(buildEvent("error",
                "{\"text\":\"Inline error\",\"code\":\"voice_missing\"}", "{\"text\":\"Additional error\"}", new byte[0]));
        var mixedError = client.readEvent(mixedInput).error();
        assertEquals("Additional error", mixedError.text());
        assertEquals("voice_missing", mixedError.code());

        var nullInput = new ByteArrayInputStream(buildEvent("error",
                "{\"text\":\"Inline error\",\"code\":\"voice_missing\"}", "{\"text\":null}", new byte[0]));
        var nullError = client.readEvent(nullInput).error();
        assertNull(nullError.text());
        assertEquals("voice_missing", nullError.code());
    }

    @Test
    void validatesInlineAudioFormatsAndExplicitOverrides() throws Exception {
        for (String metadata : List.of(
                "{}", "{\"rate\":0,\"width\":2,\"channels\":1}",
                "{\"rate\":22050.5,\"width\":2,\"channels\":1}",
                "{\"rate\":22050,\"width\":3,\"channels\":1}",
                "{\"rate\":22050,\"width\":2,\"channels\":0}"
        )) {
            var input = new ByteArrayInputStream(buildEvent("audio-start", metadata, null, new byte[0]));
            assertThrows(IOException.class, () -> client.readEvent(input));
        }
        for (String additional : List.of("{\"rate\":null}", "{\"rate\":22050.5}")) {
            var input = new ByteArrayInputStream(buildEvent("audio-start",
                    "{\"rate\":22050,\"width\":2,\"channels\":1}", additional, new byte[0]));
            assertThrows(IOException.class, () -> client.readEvent(input));
        }
    }

    @Test
    void rejectsMetadataThatIsNotAJsonObject() throws Exception {
        for (String malformed : List.of("[]", "1", "\"text\"")) {
            var inlineInput = new ByteArrayInputStream(buildEvent("error", malformed, null, new byte[0]));
            assertThrows(IOException.class, () -> client.readEvent(inlineInput));
        }
        for (String malformed : List.of("null", "[]", "1", "\"text\"", "{invalid}")) {
            var additionalInput = new ByteArrayInputStream(buildEvent("error", "{}", malformed, new byte[0]));
            assertThrows(IOException.class, () -> client.readEvent(additionalInput));
        }
    }

    @Test
    void readsAudioFormatAndPreservesFollowingBinaryPayload() throws Exception {
        var stream = new ByteArrayOutputStream();
        stream.write(buildEvent("audio-start", "{\"rate\":22050,\"width\":2,\"channels\":1,\"extra\":true}", new byte[0]));
        stream.write(buildEvent("audio-chunk", "{\"rate\":22050}", new byte[]{1, 2, 3, 4}));
        var input = new ByteArrayInputStream(stream.toByteArray());

        var start = client.readEvent(input);
        var chunk = client.readEvent(input);

        assertEquals("audio-start", start.type());
        assertEquals(new PcmAudioFormat(22050, 2, 1), start.audioFormat());
        assertEquals("audio-chunk", chunk.type());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, chunk.payload());
        assertNull(chunk.audioFormat());
        assertEquals(0, input.available());
    }

    @Test
    void rejectsMissingInvalidAndUnsupportedAudioFormatValues() throws Exception {
        for (String metadata : List.of(
                "{}", "null",
                "{\"width\":2,\"channels\":1}",
                "{\"rate\":22050,\"channels\":1}",
                "{\"rate\":22050,\"width\":2}",
                "{\"rate\":null,\"width\":2,\"channels\":1}",
                "{\"rate\":0,\"width\":2,\"channels\":1}",
                "{\"rate\":-1,\"width\":2,\"channels\":1}",
                "{\"rate\":22050.5,\"width\":2,\"channels\":1}",
                "{\"rate\":22050,\"width\":0,\"channels\":1}",
                "{\"rate\":22050,\"width\":-1,\"channels\":1}",
                "{\"rate\":22050,\"width\":3,\"channels\":1}",
                "{\"rate\":22050,\"width\":4,\"channels\":1}",
                "{\"rate\":22050,\"width\":2.5,\"channels\":1}",
                "{\"rate\":22050,\"width\":2,\"channels\":0}",
                "{\"rate\":22050,\"width\":2,\"channels\":-1}",
                "{\"rate\":22050,\"width\":2,\"channels\":3}",
                "{\"rate\":22050,\"width\":2,\"channels\":1.5}",
                "{\"rate\":1073741824,\"width\":2,\"channels\":2}"
        )) {
            var input = new ByteArrayInputStream(buildEvent("audio-start", metadata, new byte[0]));

            assertThrows(IOException.class, () -> client.readEvent(input), metadata);
        }
    }

    @Test
    void encodesSupportedFormatsAsReadableWavAndPreservesPcm() throws Exception {
        for (PcmAudioFormat format : List.of(
                new PcmAudioFormat(8000, 1, 1), new PcmAudioFormat(16000, 1, 2),
                new PcmAudioFormat(22050, 2, 1), new PcmAudioFormat(48000, 2, 2)
        )) {
            byte[] pcm = {0, 1, 2, 3};
            byte[] wav = client.encodeWav(format, pcm);
            var header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

            assertEquals(wav.length - 8, header.getInt(4));
            assertEquals(format.channels(), Short.toUnsignedInt(header.getShort(22)));
            assertEquals(format.rate(), header.getInt(24));
            assertEquals(format.rate() * format.width() * format.channels(), header.getInt(28));
            assertEquals(format.width() * format.channels(), Short.toUnsignedInt(header.getShort(32)));
            assertEquals(format.width() * 8, Short.toUnsignedInt(header.getShort(34)));
            assertEquals(pcm.length, header.getInt(40));
            try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
                assertEquals((float) format.rate(), audio.getFormat().getSampleRate());
                assertArrayEquals(pcm, audio.readAllBytes());
            }
        }
    }

    @Test
    void preservesUnsignedByteRateWithoutOverflow() throws Exception {
        var format = new PcmAudioFormat(1_073_741_823, 2, 2);
        byte[] wav = client.encodeWav(format, new byte[4]);

        assertEquals(4_294_967_292L,
                Integer.toUnsignedLong(ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(28)));
        assertThrows(IOException.class, () -> client.encodeWav(new PcmAudioFormat(1_073_741_824, 2, 2), new byte[4]));
    }

    @Test
    void rejectsIncompletePcmFrames() {
        assertThrows(IOException.class, () -> client.encodeWav(new PcmAudioFormat(22050, 2, 1), new byte[3]));
        assertThrows(IOException.class, () -> client.encodeWav(new PcmAudioFormat(48000, 2, 2), new byte[6]));
    }

    @Test
    void padsOddLengthEightBitAudioWithoutChangingDataSize() throws Exception {
        byte[] pcm = {0, 1, 2};
        byte[] wav = client.encodeWav(new PcmAudioFormat(8000, 1, 1), pcm);
        var header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(48, wav.length);
        assertEquals(40, header.getInt(4));
        assertEquals(3, header.getInt(40));
        assertEquals(0, wav[47]);
        try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            assertArrayEquals(pcm, audio.readAllBytes());
        }
    }

    @Test
    void readsTypedErrorDataAndConsumesUnknownEvents() throws Exception {
        var stream = new ByteArrayOutputStream();
        stream.write(buildEvent("future-event", "{\"unknown\":true}", new byte[]{7}));
        stream.write(buildEvent("error", "{\"text\":\"Voice unavailable — retry\",\"code\":\"voice_missing\",\"extra\":true}", new byte[0]));
        var input = new ByteArrayInputStream(stream.toByteArray());

        assertEquals("future-event", client.readEvent(input).type());
        var error = client.readEvent(input);
        assertEquals("Voice unavailable — retry", error.error().text());
        assertEquals("voice_missing", error.error().code());
        assertEquals(0, input.available());
    }

    @Test
    void acceptsEventsWithoutOptionalLengths() throws Exception {
        var event = client.readEvent(new ByteArrayInputStream("{\"type\":\"audio-stop\"}\n".getBytes(StandardCharsets.UTF_8)));

        assertEquals("audio-stop", event.type());
        assertArrayEquals(new byte[0], event.payload());
    }

    @Test
    void rejectsInvalidHeadersNegativeLengthsAndTruncatedFrames() {
        for (String frame : List.of(
                "null\n", "{}\n", "{invalid}\n", "{\"type\":\"audio-stop\"}",
                "{\"type\":\"audio-chunk\",\"data_length\":-1}\n",
                "{\"type\":\"audio-chunk\",\"payload_length\":3}\n12",
                "{\"type\":\"audio-start\",\"data_length\":10}\n{}",
                "{\"type\":\"audio-start\",\"data_length\":4}\nnull"
        )) {
            var input = new ByteArrayInputStream(frame.getBytes(StandardCharsets.UTF_8));
            assertThrows(IOException.class, () -> client.readEvent(input), frame);
        }
    }

    private byte[] buildEvent(String type, String data, byte[] payload) throws IOException {
        return buildEvent(type, null, data, payload);
    }

    private byte[] buildEvent(String type, String inlineData, String additionalData, byte[] payload) throws IOException {
        byte[] dataBytes = additionalData == null ? new byte[0] : additionalData.getBytes(StandardCharsets.UTF_8);
        String header = "{\"type\":\"%s\",\"version\":\"1.8.0\",\"payload_length\":%d%s%s,\"extra\":true}\n"
                .formatted(type, payload.length,
                        inlineData == null ? "" : ",\"data\":" + inlineData,
                        additionalData == null ? "" : ",\"data_length\":" + dataBytes.length);
        var event = new ByteArrayOutputStream();
        event.write(header.getBytes(StandardCharsets.UTF_8));
        event.write(dataBytes);
        event.write(payload);
        return event.toByteArray();
    }
}
