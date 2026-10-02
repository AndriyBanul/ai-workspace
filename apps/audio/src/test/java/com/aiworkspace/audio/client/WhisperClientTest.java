package com.aiworkspace.audio.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WhisperClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhisperClient client = new WhisperClient(
            "http://localhost",
            RestClient.builder().build(),
            objectMapper
    );

    @Test
    void parsesTimedSegmentsFromWhisperJson() throws Exception {
        var response = client.transcriptionFrom(objectMapper.readTree("""
                {
                  "text": "Hello world",
                  "language": "en",
                  "segments": [
                    {"start": 0.25, "end": 1.5, "text": " Hello"},
                    {"timestamps": {"from": "00:01.500", "to": "00:02.750"},
                     "transcript": "world", "speaker": "SPEAKER_01"}
                  ]
                }
                """));

        assertEquals("Hello world", response.text());
        assertEquals("en", response.language());
        assertEquals(2, response.segments().size());
        assertEquals(250, response.segments().get(0).startMilliseconds());
        assertEquals(1_500, response.segments().get(0).endMilliseconds());
        assertNull(response.segments().get(0).speaker());
        assertEquals(1_500, response.segments().get(1).startMilliseconds());
        assertEquals(2_750, response.segments().get(1).endMilliseconds());
        assertEquals("SPEAKER_01", response.segments().get(1).speaker());
    }
}
