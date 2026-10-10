package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenSearchKnowledgeClientTest {

    @Test
    void createsVectorIndexWithKnnEnabledAndConfiguredDimensions() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"knn\":true")))
                .andExpect(content().string(containsString("\"type\":\"knn_vector\"")))
                .andExpect(content().string(containsString("\"dimension\":3")))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"),
                builder.build(),
                new ObjectMapper(),
                new KnowledgeEmbeddingProperties(true, null, 3, null, null, null)
        );

        client.searchKnowledgeItems("workspace-1", "query", 8);

        server.verify();
    }

    @Test
    void bulkIndexesChunkMetadata() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/_bulk?refresh=wait_for"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(content().string(containsString("\"chunkId\":\"file-1:1\"")))
                .andExpect(content().string(containsString("\"pageNumber\":4")))
                .andExpect(content().string(containsString("\"startMilliseconds\":1250")))
                .andExpect(content().string(containsString("\"endMilliseconds\":3500")))
                .andExpect(content().string(containsString("\"speaker\":\"Speaker 1\"")))
                .andExpect(content().string(containsString("\"_index\":\"knowledge-items-v3\"")))
                .andRespond(withSuccess("{\"errors\":false,\"items\":[]}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"),
                builder.build(),
                new ObjectMapper()
        );

        client.addKnowledgeItems(List.of(KnowledgeItem.builder()
                .id("item-1")
                .workspaceId("workspace-1")
                .source(KnowledgeItemSource.builder()
                        .type(KnowledgeSourceType.DOCUMENT)
                        .name("report.pdf")
                        .id("file-1")
                        .build())
                .content("Revenue increased")
                .chunkMetadata(KnowledgeChunkMetadata.builder()
                        .id("file-1:1")
                        .sequence(1)
                        .heading("Revenue")
                        .pageNumber(4)
                        .startMilliseconds(1_250L)
                        .endMilliseconds(3_500L)
                        .speaker("Speaker 1")
                        .build())
                .createdAt(Instant.parse("2026-09-08T09:00:00Z"))
                .build()));

        server.verify();
    }

    @Test
    void refusesToPublishIncompleteStagedGeneration() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/_bulk?refresh=wait_for"))
                .andExpect(content().string(containsString("\"sourceGeneration\":\"file-1:generation-2\"")))
                .andRespond(withSuccess("{\"errors\":false,\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_count"))
                .andExpect(content().string(containsString("\"sourceGeneration\":\"file-1:generation-2\"")))
                .andRespond(withSuccess("{\"count\":0}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper());
        KnowledgeItem item = KnowledgeItem.builder()
                .id("file-1:generation-2:0")
                .workspaceId("workspace-1")
                .source(KnowledgeItemSource.builder()
                        .id("file-1")
                        .generation("generation-2")
                        .type(KnowledgeSourceType.DOCUMENT)
                        .build())
                .content("Replacement")
                .createdAt(Instant.now())
                .build();

        assertThrows(UpstreamServiceException.class,
                () -> client.stageKnowledgeItems("workspace-1", "file-1", "generation-2", List.of(item)));
        server.verify();
    }

    @Test
    void replacesSourceThenPrunesObsoleteChunkIds() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/_bulk?refresh=wait_for"))
                .andRespond(withSuccess("{\"errors\":false,\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://localhost:9200/knowledge-items-v3/_delete_by_query?refresh=true&conflicts=proceed"
                ))
                .andExpect(content().string(containsString("\"workspaceId\":\"workspace-1\"")))
                .andExpect(content().string(containsString("\"sourceId\":\"file-1\"")))
                .andExpect(content().string(containsString("\"ids\":{\"values\":[\"file-1:1\"]}")))
                .andRespond(withSuccess("{\"timed_out\":false,\"failures\":[]}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
        );
        KnowledgeItem replacement = KnowledgeItem.builder()
                .id("file-1:1")
                .workspaceId("workspace-1")
                .source(KnowledgeItemSource.builder()
                        .id("file-1")
                        .type(KnowledgeSourceType.DOCUMENT)
                        .build())
                .content("Replacement")
                .createdAt(Instant.now())
                .build();

        client.replaceKnowledgeItems("workspace-1", "file-1", List.of(replacement));

        server.verify();
    }

    @Test
    void deletesAllKnowledgeForSourceWithinWorkspace() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo(
                        "http://localhost:9200/knowledge-items-v3/_delete_by_query?refresh=true&conflicts=proceed"
                ))
                .andExpect(content().string(containsString("\"workspaceId\":\"workspace-1\"")))
                .andExpect(content().string(containsString("\"sourceId\":\"file-1\"")))
                .andRespond(withSuccess("{\"timed_out\":false,\"failures\":[]}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
        );

        client.deleteKnowledgeItemsBySourceId("workspace-1", "file-1");

        server.verify();
    }

    @Test
    void deletesAllKnowledgeForWorkspace() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo(
                        "http://localhost:9200/knowledge-items-v3/_delete_by_query?refresh=true&conflicts=proceed"
                ))
                .andExpect(content().string(containsString("\"workspaceId\":\"workspace-1\"")))
                .andRespond(withSuccess("{\"timed_out\":false,\"failures\":[]}", MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
        );

        client.deleteKnowledgeItemsByWorkspaceId("workspace-1");

        server.verify();
    }

    @Test
    void searchesChunkContentAndBoostsHeadings() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andExpect(content().string(containsString("\"multi_match\"")))
                .andExpect(content().string(containsString("\"heading^2\"")))
                .andExpect(content().string(containsString("\"speaker^2\"")))
                .andExpect(content().string(containsString("\"embedding\"")))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"_id":"item-1","_source":{
                          "id":"item-1","workspaceId":"workspace-1","sourceType":"documents",
                          "sourceName":"report.pdf","sourceId":"file-1","content":"Revenue increased",
                          "chunkId":"file-1:1","chunkSequence":1,"heading":"Revenue","pageNumber":4,
                          "startMilliseconds":1250,"endMilliseconds":3500,"speaker":"Speaker 1",
                          "createdAt":"2026-09-08T09:00:00Z"
                        }}]}}
                        """, MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"),
                builder.build(),
                new ObjectMapper()
        );

        List<KnowledgeItem> results = client.searchKnowledgeItems("workspace-1", "revenue", 8);

        assertEquals(1, results.size());
        assertEquals("file-1:1", results.get(0).chunkId());
        assertEquals(4, results.get(0).pageNumber());
        assertEquals(1_250L, results.get(0).startMilliseconds());
        assertEquals(3_500L, results.get(0).endMilliseconds());
        assertEquals("Speaker 1", results.get(0).speaker());
        server.verify();
    }

    @Test
    void combinesLexicalAndVectorRanksWithRrf() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andRespond(withSuccess(buildSearchResponse("lexical", "Lexical only"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andExpect(content().string(containsString("\"knn\"")))
                .andExpect(content().string(containsString("\"vector\":[1.0,0.0,0.0]")))
                .andExpect(content().string(containsString("\"workspaceId\":\"workspace-1\"")))
                .andRespond(withSuccess(buildSearchResponse("vector", "Semantic match"), MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"),
                builder.build(),
                new ObjectMapper(),
                new KnowledgeEmbeddingProperties(true, null, 3, null, 2, 60)
        );

        List<KnowledgeItem> results = client.searchKnowledgeItems(
                "workspace-1", "revenue", List.of(1.0f, 0.0f, 0.0f), 2, 2, 60
        );

        assertEquals(List.of("lexical", "vector"), results.stream().map(KnowledgeItem::id).toList());
        server.verify();
    }

    @Test
    void searchesVectorOnlyWithoutRunningBm25() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andExpect(content().string(containsString("\"knn\"")))
                .andExpect(content().string(containsString("\"vector\":[1.0,0.0,0.0]")))
                .andRespond(withSuccess(buildSearchResponse("vector", "Semantic match"), MediaType.APPLICATION_JSON));
        OpenSearchKnowledgeClient client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"),
                builder.build(),
                new ObjectMapper(),
                new KnowledgeEmbeddingProperties(true, null, 3, null, 2, 60)
        );

        List<KnowledgeItem> results = client.searchKnowledgeItemsByVector(
                "workspace-1", List.of(1.0f, 0.0f, 0.0f), 2, 2
        );

        assertEquals(List.of("vector"), results.stream().map(KnowledgeItem::id).toList());
        server.verify();
    }

    @Test
    void rejectsMissingBulkStatusAndMalformedBulkResponses() {
        for (String response : List.of("null", "{}", "{invalid", "{\"errors\":true}")) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/_bulk?refresh=wait_for"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var client = OpenSearchKnowledgeClientTestFactory.create(
                    URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
            );
            var item = KnowledgeItem.builder().id("item-1").workspaceId("workspace-1")
                    .content("Content").createdAt(Instant.EPOCH).build();

            assertThrows(UpstreamServiceException.class, () -> client.addKnowledgeItems(List.of(item)), response);
            server.verify();
        }
    }

    @Test
    void rejectsDeletionTimeoutsFailuresAndMissingFailureList() {
        for (String response : List.of(
                "null", "{}", "{invalid", "{\"timed_out\":true,\"failures\":[]}",
                "{\"timed_out\":false,\"failures\":[{\"index\":\"knowledge-items-v3\",\"status\":500}]}"
        )) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_delete_by_query?refresh=true&conflicts=proceed"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var client = OpenSearchKnowledgeClientTestFactory.create(
                    URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
            );

            assertThrows(UpstreamServiceException.class,
                    () -> client.deleteKnowledgeItemsBySourceId("workspace-1", "file-1"), response);
            server.verify();
        }
    }

    @Test
    void preservesLegacySearchDefaultsFallbackIdsAndDeduplication() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andRespond(withSuccess("""
                        {"took":1,"hits":{"total":{"value":2},"hits":[
                          {"_id":"legacy-id","_score":1.5,"_source":{
                            "workspaceId":"workspace-1","sourceType":"future-type","extra":true
                          }},
                          {"_id":"legacy-id","_source":null}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        var client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
        );

        var results = client.searchKnowledgeItems("workspace-1", "query", 8);

        assertEquals(1, results.size());
        var item = results.getFirst();
        assertEquals("legacy-id", item.id());
        assertEquals("workspace-1", item.workspaceId());
        assertEquals(KnowledgeSourceType.DOCUMENT, item.source().type());
        assertEquals("", item.content());
        assertEquals(Instant.EPOCH, item.createdAt());
        assertNull(item.embeddingMetadata().vector());
        assertNull(item.chunkMetadata().sequence());
        server.verify();
    }

    @Test
    void parsesSourceDatesAndEmbeddingMetadataFromTypedSearchHits() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"_id":"item-1","_source":{
                          "workspaceId":"workspace-1","sourceType":"audio","sourceId":"file-1",
                          "generation":"g1","extractedAt":"2026-09-08T09:00:00Z",
                          "embedding":[0.25,0.75],"embeddingModel":"test-model","embeddingDimensions":2,
                          "contentHash":"hash","createdAt":"2026-09-08T09:00:00Z"
                        }}]}}
                        """, MediaType.APPLICATION_JSON));
        var client = OpenSearchKnowledgeClientTestFactory.create(
                URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
        );

        var item = client.searchKnowledgeItems("workspace-1", "query", 8).getFirst();

        assertEquals(Instant.parse("2026-09-08T09:00:00Z"), item.source().extractedAt());
        assertEquals("g1", item.source().generation());
        assertEquals(List.of(0.25f, 0.75f), item.embeddingMetadata().vector());
        assertEquals("test-model", item.embeddingMetadata().model());
        assertEquals(2, item.embeddingMetadata().dimensions());
        assertEquals("hash", item.embeddingMetadata().contentHash());
        server.verify();
    }

    @Test
    void rejectsInvalidSearchResponsesAndSourceDates() {
        for (String response : List.of(
                "null", "{}", "{invalid", "{\"hits\":{\"hits\":{}}}",
                "{\"hits\":{\"hits\":[{\"_source\":{\"createdAt\":\"invalid\"}}]}}"
        )) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_mapping")).andRespond(withSuccess());
            server.expect(requestTo("http://localhost:9200/knowledge-items-v3/_search"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var client = OpenSearchKnowledgeClientTestFactory.create(
                    URI.create("http://localhost:9200"), builder.build(), new ObjectMapper()
            );

            assertThrows(UpstreamServiceException.class,
                    () -> client.searchKnowledgeItems("workspace-1", "query", 8), response);
            server.verify();
        }
    }

    private String buildSearchResponse(String id, String content) {
        return """
                {"hits":{"hits":[{"_id":"%s","_source":{
                  "id":"%s","workspaceId":"workspace-1","sourceType":"documents",
                  "content":"%s","createdAt":"2026-09-08T09:00:00Z"
                }}]}}
                """.formatted(id, id, content);
    }
}
