package com.aiworkspace.knowledge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                URI.create("https://gemini.example/v1beta"),
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
                URI.create("https://gemini.example/v1beta"),
                "test-key",
                "test-model",
                builder.build(),
                new ObjectMapper()
        );

        server.expect(requestTo("https://gemini.example/v1beta/models/test-model:generateContent?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("optimized search queries")))
                .andExpect(content().string(containsString("likely exact facts")))
                .andExpect(content().string(containsString("responseSchema")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"queries\\":[\\"221B Baker Street\\",\\"Holmes residence\\"]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(
                java.util.List.of("221B Baker Street", "Holmes residence"),
                client.expand("What is Sherlock Holmes's address?", 2)
        );
        server.verify();
    }
}
