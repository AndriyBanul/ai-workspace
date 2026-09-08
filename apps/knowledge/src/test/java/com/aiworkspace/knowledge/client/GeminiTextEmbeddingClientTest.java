package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiTextEmbeddingClientTest {

    @Test
    void batchesDocumentEmbeddingsAndNormalizesVectors() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1beta/models/gemini-embedding-001:batchEmbedContents?key=secret"))
                .andExpect(content().string(containsString("\"taskType\":\"RETRIEVAL_DOCUMENT\"")))
                .andExpect(content().string(containsString("\"outputDimensionality\":3")))
                .andRespond(withSuccess(
                        "{\"embeddings\":[{\"values\":[3,4,0]},{\"values\":[0,0,2]}]}",
                        MediaType.APPLICATION_JSON
                ));
        GeminiTextEmbeddingClient client = client(builder, "secret");

        List<List<Float>> embeddings = client.embedDocuments(List.of("first", "second"));

        assertEquals(List.of(0.6f, 0.8f, 0.0f), embeddings.get(0));
        assertEquals(List.of(0.0f, 0.0f, 1.0f), embeddings.get(1));
        server.verify();
    }

    @Test
    void marksProviderUnconfiguredWithoutApiKey() {
        RestClient.Builder builder = RestClient.builder();
        GeminiTextEmbeddingClient client = client(builder, " ");

        assertFalse(client.isConfigured());
    }

    @Test
    void marksQueryEmbeddingsForRetrievalQueries() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1beta/models/gemini-embedding-001:batchEmbedContents?key=secret"))
                .andExpect(content().string(containsString("\"taskType\":\"RETRIEVAL_QUERY\"")))
                .andRespond(withSuccess(
                        "{\"embeddings\":[{\"values\":[0,3,4]}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<Float> embedding = client(builder, "secret").embedQuery("What grew?");

        assertEquals(List.of(0.0f, 0.6f, 0.8f), embedding);
        server.verify();
    }

    private GeminiTextEmbeddingClient client(RestClient.Builder builder, String apiKey) {
        return new GeminiTextEmbeddingClient(
                URI.create("https://example.test/v1beta"),
                apiKey,
                new KnowledgeEmbeddingProperties(true, "gemini-embedding-001", 3, 32, 32, 60),
                builder.build(),
                new ObjectMapper()
        );
    }
}
