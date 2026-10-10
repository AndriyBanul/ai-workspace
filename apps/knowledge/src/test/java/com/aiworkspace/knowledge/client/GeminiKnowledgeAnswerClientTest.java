package com.aiworkspace.knowledge.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiKnowledgeAnswerClientTest {

    @Test
    void instructsModelToReviewAllChunksBeforeAnswering() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new GeminiKnowledgeAnswerClient(
                "https://gemini.example/v1beta",
                "test-key",
                "test-model",
                builder.build(),
                new ObjectMapper()
        );

        server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("Review every context chunk before deciding on the answer")))
                .andExpect(content().string(containsString("including later chunks")))
                .andExpect(content().string(containsString("resolve pronouns and other implicit references")))
                .andExpect(content().string(containsString("include the final outcome or explanation")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Combined answer"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("Combined answer", client.answer("What happened?", "Chunk one\nChunk two"));
        server.verify();
    }

    @Test
    void generatesDistinctSearchQueriesAsStructuredJson() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new GeminiKnowledgeAnswerClient(
                "https://gemini.example/v1beta",
                "test-key",
                "test-model",
                builder.build(),
                new ObjectMapper()
        );

        server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("optimized search queries")))
                .andExpect(content().string(containsString("Search query rewrite policy v2")))
                .andExpect(content().string(containsString("specific attribute or relationship")))
                .andExpect(content().string(containsString("Do not answer the question or insert guessed answer values")))
                .andExpect(content().string(containsString("The original question is searched separately")))
                .andExpect(content().string(containsString("responseSchema")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"queries\\":[\\"Sherlock Holmes address\\",\\"Where does Holmes live?\\"]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(
                java.util.List.of("Sherlock Holmes address", "Where does Holmes live?"),
                client.expand("What is Sherlock Holmes's address?", 2)
        );
        server.verify();
    }

    @Test
    void joinsAnswerPartsAndIgnoresAdditionalProviderFields() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                .andRespond(withSuccess("""
                        {"usageMetadata":{},"candidates":[{"finishReason":"STOP","index":0,
                          "content":{"role":"model","parts":[
                            {"text":" Combined"},{"text":null},{"text":" answer "}
                          ]}}]}
                        """, MediaType.APPLICATION_JSON));
        var client = createClient(builder.build());

        assertEquals("Combined answer", client.answer("What happened?", "Context"));
        server.verify();
    }

    @Test
    void trimsQueriesRemovesBlanksDuplicatesAndOriginalAndAppliesLimit() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String response = buildResponse("""
                {"extra":true,"queries":[null," "," ORIGINAL QUESTION ",
                 " first query ","first query","second query","third query"]}
                """);
        server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        var client = createClient(builder.build());

        assertEquals(List.of("first query", "second query"), client.expand("Original question", 2));
        server.verify();
    }

    @Test
    void wrapsInvalidQueryPayloadsAsUpstreamFailures() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        List<String> payloads = List.of("null", "{}", "{invalid", "{\"queries\":{}}");
        for (String payload : payloads) {
            server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                    .andRespond(withSuccess(buildResponse(payload), MediaType.APPLICATION_JSON));
        }
        var client = createClient(builder.build());

        for (String payload : payloads) {
            var exception = assertThrows(UpstreamServiceException.class, () -> client.expand("Question", 2), payload);
            assertEquals("Gemini returned invalid search queries", exception.getMessage());
        }
        server.verify();
    }

    @Test
    void rejectsInvalidOrIncompleteAnswerEnvelopes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        List<String> responses = List.of(
                "null", "{invalid", "{}", "{\"candidates\":[]}",
                "{\"candidates\":[{\"content\":{}}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" \"}]}}]}"
        );
        for (String response : responses) {
            server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
        var client = createClient(builder.build());

        for (String response : responses) {
            assertThrows(UpstreamServiceException.class, () -> client.answer("Question", "Context"), response);
        }
        server.verify();
    }

    private GeminiKnowledgeAnswerClient createClient(RestClient restClient) {
        return new GeminiKnowledgeAnswerClient(
                "https://gemini.example/v1beta", "test-key", "test-model", restClient, new ObjectMapper()
        );
    }

    private String buildResponse(String payload) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", payload)))
        ))));
    }
}
