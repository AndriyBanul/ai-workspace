package com.aiworkspace.videos.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiVideoClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiVideoClient client = new GeminiVideoClient(
            "http://localhost",
            "test-key",
            "test-model",
            RestClient.builder().build(),
            objectMapper
    );

    @Test
    void parsesStructuredVideoAnalysis() throws Exception {
        String analysis = """
                {"summary":"A presenter addresses the camera.","transcript":"Hello.","language":"en",
                 "segments":[{"startMilliseconds":100,"endMilliseconds":900,
                 "speaker":"Presenter","text":"Hello."}]}
                """;
        String response = objectMapper.writeValueAsString(objectMapper.createObjectNode().set(
                "candidates",
                objectMapper.createArrayNode().add(objectMapper.createObjectNode().set(
                        "content",
                        objectMapper.createObjectNode().set(
                                "parts",
                                objectMapper.createArrayNode().add(
                                        objectMapper.createObjectNode().put("text", analysis)
                                )
                        )
                ))
        ));

        var result = client.parseAnalysis(response);

        assertEquals("A presenter addresses the camera.", result.summary());
        assertEquals("Hello.", result.transcript());
        assertEquals("en", result.language());
        assertEquals(100, result.segments().getFirst().startMilliseconds());
        assertEquals("Presenter", result.segments().getFirst().speaker());
    }

    @Test
    void requestsJsonSchemaForSummaryAndTranscript() throws Exception {
        String body = client.buildRequestBody(new byte[] {1, 2}, "video/mp4", "Analyze the video.");

        assertTrue(body.contains("application/json"));
        assertTrue(body.contains("startMilliseconds"));
        assertTrue(body.contains("transcript"));
    }

    @Test
    void sendsYouTubeUrlAsProviderFileData() throws Exception {
        String body = client.buildYouTubeRequestBody(
                "https://www.youtube.com/watch?v=9hE5-98ZeCg",
                "Analyze the video."
        );
        var json = objectMapper.readTree(body);

        assertEquals(
                "https://www.youtube.com/watch?v=9hE5-98ZeCg",
                json.path("contents").path(0).path("parts").path(0)
                        .path("file_data").path("file_uri").asText()
        );
        assertEquals("application/json", json.path("generationConfig").path("responseMimeType").asText());
    }

    @Test
    void normalizesAnalysisAndIgnoresAdditionalProviderFields() throws Exception {
        var result = client.parseAnalysis(buildResponse("""
                {"summary":" Summary ","transcript":" Hello ","language":" en ","extra":true,
                 "segments":[
                   {"startMilliseconds":-100,"endMilliseconds":200,"text":" Hello ","speaker":" S1 ","id":1},
                   {"startMilliseconds":900,"endMilliseconds":100,"text":"world","speaker":" "},
                   {"text":" "},null
                 ]}
                """));

        assertEquals("Summary", result.summary());
        assertEquals("Hello", result.transcript());
        assertEquals("en", result.language());
        assertEquals(List.of(
                new TranscriptSegment(0, 200, "S1", "Hello"),
                new TranscriptSegment(900, 900, null, "world")
        ), result.segments());
    }

    @Test
    void defaultsMissingOptionalAnalysisFields() throws Exception {
        var result = client.parseAnalysis(buildResponse("{\"summary\":\"No speech\"}"));

        assertEquals("", result.transcript());
        assertNull(result.language());
        assertEquals(List.of(), result.segments());
    }

    @Test
    void rejectsInvalidAnalysisAndMissingSummary() throws Exception {
        for (String analysis : List.of("null", "{}", "{invalid", "{\"summary\":\" \"}")) {
            String response = buildResponse(analysis);
            assertThrows(UpstreamServiceException.class, () -> client.parseAnalysis(response));
        }
    }

    @Test
    void rejectsInvalidOrIncompleteResponseEnvelopes() {
        for (String body : List.of(
                "", "null", "{invalid", "{}", "{\"candidates\":[]}",
                "{\"candidates\":[{\"content\":{}}]}"
        )) {
            assertThrows(UpstreamServiceException.class, () -> client.parseAnalysis(body));
        }
    }

    private String buildResponse(String analysis) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "usageMetadata", Map.of("totalTokenCount", 10),
                "candidates", List.of(Map.of(
                        "finishReason", "STOP", "content", Map.of(
                                "role", "model", "parts", List.of(Map.of("text", analysis))
                        )
                ))
        ));
    }
}
