package com.aiworkspace.audio.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhisperClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhisperClient client = new WhisperClient(
            "http://localhost",
            RestClient.builder().build(),
            objectMapper
    );

    @Test
    void parsesTimedSegmentsFromWhisperJson() {
        var response = client.parseTranscription("""
                {
                  "text": "Hello world",
                  "language": "en",
                  "segments": [
                    {"start": 0.25, "end": 1.5, "text": " Hello"},
                    {"timestamps": {"from": "00:01.500", "to": "00:02.750"},
                     "transcript": "world", "speaker": "SPEAKER_01"}
                  ]
                }
                """);

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

    @Test
    void ignoresAdditionalProviderFields() {
        var response = client.parseTranscription("""
                {
                  "text": "Hello", "language": "en", "duration": 1.5,
                  "segments": [{
                    "id": 0, "text": " Hello ", "words": [],
                    "timestamps": {"from": 0.25, "to": 1.5, "extra": true}
                  }]
                }
                """);

        assertEquals(List.of(new TranscriptSegment(250, 1_500, null, "Hello")), response.segments());
    }

    @Test
    void defaultsMissingAndNullOptionalFields() {
        for (String body : List.of("{}", "{\"text\":null,\"language\":null,\"segments\":null}")) {
            var response = client.parseTranscription(body);

            assertEquals("", response.text());
            assertEquals("", response.language());
            assertEquals(List.of(), response.segments());
        }
    }

    @Test
    void preservesTimestampFallbacksAndSegmentNormalization() {
        var response = client.parseTranscription("""
                {
                  "segments": [
                    {"text": " ", "transcript": " hello ", "start": "1.25", "end": 0.5,
                     "timestamps": {"from": "00:09", "to": "00:10"}, "speaker": " S1 "},
                    {"text": "world", "start": null, "end": null,
                     "timestamps": {"from": "00:01:02.500", "to": "00:01:03"}, "speaker": " "},
                    {"text": "invalid timestamps", "start": "invalid", "end": -1},
                    {"text": "missing timestamps"},
                    {"text": " ", "transcript": " "},
                    null
                  ]
                }
                """);

        assertEquals(List.of(
                new TranscriptSegment(1_250, 1_250, "S1", "hello"),
                new TranscriptSegment(62_500, 63_000, null, "world"),
                new TranscriptSegment(0, 0, null, "invalid timestamps"),
                new TranscriptSegment(0, 0, null, "missing timestamps")
        ), response.segments());
    }

    @Test
    void wrapsInvalidResponsesAsUpstreamFailures() {
        for (String body : List.of("", "null", "{invalid", "{\"segments\":{}}")) {
            var exception = assertThrows(UpstreamServiceException.class, () -> client.parseTranscription(body));

            assertEquals("Whisper", exception.serviceName());
            assertEquals("Whisper returned an invalid response", exception.getMessage());
        }
        assertThrows(UpstreamServiceException.class, () -> client.parseTranscription(null));
    }
}
