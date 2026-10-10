package com.aiworkspace.images.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiOcrClientTest {
    @Test
    void transcribesRegionsAndAcceptsBlankPages() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var mapper = new ObjectMapper();
        for (String payload : new String[]{"{\"regions\":[{\"text\":\"Visible text\"}]}", "{\"regions\":[]}"}) {
            server.expect(requestTo("https://example.test/models/test:generateContent"))
                    .andExpect(header("x-goog-api-key", "key"))
                    .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
                            + mapper.writeValueAsString(payload) + "}]}}]}", MediaType.APPLICATION_JSON));
        }
        var client = new GeminiOcrClient(builder.build(), mapper, "https://example.test", "key", "test");
        var result = client.recognize(new byte[]{1}, "image/png");
        assertEquals("Visible text", result.joinRegionText());
        assertNull(result.regions().getFirst().confidence());
        assertNull(result.regions().getFirst().bounds());
        assertTrue(client.recognize(new byte[]{1}, "image/png").regions().isEmpty());
        server.verify();
    }

    @Test
    void rejectsTruncatedResponseAndMissingKey() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/models/test:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}", MediaType.APPLICATION_JSON));
        var client = new GeminiOcrClient(builder.build(), new ObjectMapper(), "https://example.test", "key", "test");
        assertThrows(UpstreamServiceException.class, () -> client.recognize(new byte[]{1}, "image/png"));
        var unconfigured = new GeminiOcrClient(builder.build(), new ObjectMapper(), "https://example.test", "", "test");
        assertThrows(UpstreamServiceException.class, () -> unconfigured.recognize(new byte[]{1}, "image/png"));
        server.verify();
    }

    @Test
    void skipsThoughtPartsAndBlankRegionsAndIgnoresAdditionalFields() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/models/test:generateContent"))
                .andRespond(withSuccess("""
                        {"usageMetadata":{},"candidates":[{"finishReason":"STOP","index":0,
                          "content":{"role":"model","parts":[
                            {"thought":true,"text":"This is internal reasoning."},
                            {"text":"{\\\"regions\\\":[{\\\"text\\\":\\\" Visible text \\\",\\\"extra\\\":true},{\\\"text\\\":\\\" \\\"}]}"}
                          ]}}]}
                        """, MediaType.APPLICATION_JSON));
        var client = new GeminiOcrClient(builder.build(), new ObjectMapper(), "https://example.test", "key", "test");

        var result = client.recognize(new byte[]{1}, "image/png");

        assertEquals(1, result.regions().size());
        assertEquals("Visible text", result.joinRegionText());
        server.verify();
    }

    @Test
    void rejectsMissingRegionsAndNonStringRegionText() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var mapper = new ObjectMapper();
        List<String> payloads = List.of(
                "null", "{}", "{\"regions\":{}}", "{\"regions\":[null]}",
                "{\"regions\":[{}]}", "{\"regions\":[{\"text\":null}]}",
                "{\"regions\":[{\"text\":123}]}", "{\"regions\":[{\"text\":true}]}"
        );
        for (String payload : payloads) {
            String response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                    "finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", payload)))
            ))));
            server.expect(requestTo("https://example.test/models/test:generateContent"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
        var client = new GeminiOcrClient(builder.build(), mapper, "https://example.test", "key", "test");

        for (String payload : payloads) {
            assertThrows(UpstreamServiceException.class, () -> client.recognize(new byte[]{1}, "image/png"), payload);
        }
        server.verify();
    }
}
