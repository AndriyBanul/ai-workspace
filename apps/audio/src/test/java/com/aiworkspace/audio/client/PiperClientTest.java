package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.PcmAudioFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PiperClientTest {

    private final PiperClient client = new PiperClient("localhost", 10200, new ObjectMapper());

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
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        String header = "{\"type\":\"%s\",\"version\":\"1.8.0\",\"data_length\":%d,\"payload_length\":%d,\"extra\":true}\n"
                .formatted(type, dataBytes.length, payload.length);
        var event = new ByteArrayOutputStream();
        event.write(header.getBytes(StandardCharsets.UTF_8));
        event.write(dataBytes);
        event.write(payload);
        return event.toByteArray();
    }
}
