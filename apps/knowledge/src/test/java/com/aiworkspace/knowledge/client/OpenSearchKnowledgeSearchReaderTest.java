package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.entities.SourceIndexManifestEntity;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.services.SourceIndexManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenSearchKnowledgeSearchReaderTest {

    @Test
    void searchesOnlyPublishedGenerationForUpgradedSource() throws Exception {
        SourceIndexManifestEntity manifest = new SourceIndexManifestEntity(
                "source-1", "workspace-1", "generation-2", 1, Instant.now());
        SearchContext context = context(List.of(manifest));
        context.server().expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andExpect(content().string(containsString("source-1:generation-2")))
                .andExpect(content().string(not(containsString("source-1:generation-1"))))
                .andExpect(content().string(containsString("\"sourceId\":[\"source-1\"]")))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        assertTrue(context.reader().search("workspace-1", "report", 5).isEmpty());
        context.server().verify();
    }

    @Test
    void hidesUnpublishedGenerationsWhenNoManifestExists() throws Exception {
        SearchContext context = context(List.of());
        context.server().expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andExpect(content().string(containsString("\"must_not\":[{\"exists\":{\"field\":\"sourceGeneration\"}}]")))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        assertTrue(context.reader().search("workspace-1", "report", 5).isEmpty());
        context.server().verify();
    }

    private SearchContext context(List<SourceIndexManifestEntity> active) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        SourceIndexManifestService manifests = new SourceIndexManifestService(null) {
            @Override
            public List<SourceIndexManifestEntity> findByWorkspaceId(String workspaceId) {
                return active;
            }
        };
        OpenSearchKnowledgeStore store = new OpenSearchKnowledgeStore(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper(),
                new KnowledgeEmbeddingProperties(null, null, null, null, null, null));
        return new SearchContext(new OpenSearchKnowledgeSearchReader(store, 2, SearchTelemetry.NOOP, manifests),
                server);
    }

    private record SearchContext(OpenSearchKnowledgeSearchReader reader, MockRestServiceServer server) {
    }
}
