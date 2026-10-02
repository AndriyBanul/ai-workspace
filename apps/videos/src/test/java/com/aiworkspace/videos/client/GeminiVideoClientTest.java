package com.aiworkspace.videos.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        var result = client.analysisFrom(response);

        assertEquals("A presenter addresses the camera.", result.summary());
        assertEquals("Hello.", result.transcript());
        assertEquals("en", result.language());
        assertEquals(100, result.segments().getFirst().startMilliseconds());
        assertEquals("Presenter", result.segments().getFirst().speaker());
    }

    @Test
    void requestsJsonSchemaForSummaryAndTranscript() throws Exception {
        String body = client.requestBody(new byte[] {1, 2}, "video/mp4", "Analyze the video.");

        assertTrue(body.contains("application/json"));
        assertTrue(body.contains("startMilliseconds"));
        assertTrue(body.contains("transcript"));
    }

    @Test
    void sendsYouTubeUrlAsProviderFileData() throws Exception {
        String body = client.youtubeRequestBody(
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
}
