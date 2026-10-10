package com.aiworkspace.images.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiImageClientTest {

    private final GeminiImageClient client = new GeminiImageClient(
            "http://localhost", "test-key", "test-model", RestClient.builder().build(), new ObjectMapper()
    );

    @Test
    void joinsTextPartsAndIgnoresAdditionalProviderFields() {
        var description = client.parseDescription("""
                {"usageMetadata": {"totalTokenCount": 10}, "candidates": [{
                  "finishReason": "STOP", "index": 0,
                  "content": {"role": "model", "parts": [
                    {"text": " A mountain", "extra": true},
                    {"inlineData": {"mimeType": "image/png", "data": "ignored"}},
                    {"text": null}, {"text": " landscape. "}
                  ]}
                }]}
                """);

        assertEquals("A mountain landscape.", description);
    }

    @Test
    void rejectsMissingCandidatesContentAndEmptyDescriptions() {
        for (String body : List.of(
                "null", "{}", "{\"candidates\":[]}", "{\"candidates\":[null]}",
                "{\"candidates\":[{\"content\":{}}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" \"}]}}]}"
        )) {
            assertThrows(UpstreamServiceException.class, () -> client.parseDescription(body));
        }
    }

    @Test
    void wrapsMalformedResponsesAsUpstreamFailures() {
        for (String body : List.of("", "{invalid", "{\"candidates\":{}}")) {
            var exception = assertThrows(UpstreamServiceException.class, () -> client.parseDescription(body));

            assertEquals("Gemini", exception.serviceName());
            assertEquals("Gemini returned an invalid response", exception.getMessage());
        }
    }
}
