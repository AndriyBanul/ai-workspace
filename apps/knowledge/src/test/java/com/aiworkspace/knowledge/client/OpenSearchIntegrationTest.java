package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("opensearch")
class OpenSearchIntegrationTest {
    private static final String URL = "http://localhost:19200";
    private static final String INDEX = "/knowledge-items-v3";
    private final RestClient http = RestClient.create(URL);
    private final ObjectMapper json = new ObjectMapper();
    private OpenSearchKnowledgeClient client;

    @BeforeEach
    void resetOnlyIsolatedTestCluster() throws Exception {
        var info = json.readTree(http.get().uri("/").retrieve().body(String.class));
        assertEquals("ai-workspace-integration-test", info.path("cluster_name").asText(),
                "Refusing to reset a cluster that is not the isolated test cluster");
        http.delete().uri(INDEX).retrieve().onStatus(status -> status.value() == 404,
                (request, response) -> {}).toBodilessEntity();
        client = new OpenSearchKnowledgeClient(URI.create(URL), RestClient.create(), json,
                new KnowledgeEmbeddingProperties(true, "test", 3, 32, 32, 60));
    }

    @Test
    void createsMappingAndBulkIndexesMetadata() throws Exception {
        client.addKnowledgeItems(List.of(item("one", "a", "source", "Zuschuss für Mitarbeitende — Привіт", List.of(1f, 0f, 0f))));
        var mapping = json.readTree(http.get().uri(INDEX + "/_mapping").retrieve().body(String.class))
                .path("knowledge-items-v3").path("mappings").path("properties");
        assertEquals("knn_vector", mapping.path("embedding").path("type").asText());
        assertEquals(3, mapping.path("embedding").path("dimension").asInt());
        assertEquals("lucene", mapping.path("embedding").path("method").path("engine").asText());
        assertEquals("keyword", mapping.path("workspaceId").path("type").asText());
        assertEquals("long", mapping.path("startMilliseconds").path("type").asText());
        assertEquals("long", mapping.path("endMilliseconds").path("type").asText());
        assertEquals("keyword", mapping.path("speaker").path("type").asText());
        var settings = json.readTree(http.get().uri(INDEX + "/_settings").retrieve().body(String.class));
        assertTrue(settings.path("knowledge-items-v3").path("settings").path("index").path("knn").asBoolean());
        var results = client.findKnowledgeItemsByWorkspaceId("a");
        assertEquals(1, results.size());
        assertEquals("one", results.getFirst().chunkId());
        assertEquals("Zuschuss für Mitarbeitende — Привіт", results.getFirst().content());
        assertEquals(2, results.getFirst().pageNumber());
        assertEquals(1_250L, results.getFirst().startMilliseconds());
        assertEquals(3_500L, results.getFirst().endMilliseconds());
        assertEquals("Speaker 1", results.getFirst().speaker());
    }

    @Test
    void hybridSearchFindsVectorOnlyMatchAndFiltersOtherWorkspace() throws Exception {
        client.addKnowledgeItems(List.of(
                item("vector", "a", "s", "Automobiles", List.of(1f, 0f, 0f)),
                item("lexical", "a", "s", "cars", List.of(0f, 1f, 0f)),
                item("foreign", "b", "s", "cars", List.of(1f, 0f, 0f))));
        assertEquals(List.of("lexical"), client.searchKnowledgeItems("a", "cars", 8).stream()
                .map(KnowledgeItem::id).toList());
        var ids = client.searchKnowledgeItems("a", "cars", List.of(1f, 0f, 0f), 8, 1, 60)
                .stream().map(KnowledgeItem::id).toList();
        assertTrue(ids.contains("vector"), "kNN must contribute a chunk with no lexical match");
        assertTrue(ids.contains("lexical"));
        assertFalse(ids.contains("foreign"));
        var vectorOnly = client.searchKnowledgeItems("a", "unmatchedterm", List.of(1f, 0f, 0f), 1, 1, 60);
        assertEquals("vector", vectorOnly.getFirst().id());
    }

    @Test
    void replacementAndDeletionPreserveOtherSourcesAndWorkspaces() throws Exception {
        client.addKnowledgeItems(List.of(
                item("old", "a", "s", "old", List.of(1f, 0f, 0f)),
                item("keep", "a", "other", "keep", List.of(1f, 0f, 0f)),
                item("foreign", "b", "s", "keep", List.of(1f, 0f, 0f))));
        client.replaceKnowledgeItems("a", "s", List.of(item("new", "a", "s", "new", List.of(1f, 0f, 0f))));
        assertEquals(java.util.Set.of("new", "keep"), client.findKnowledgeItemsByWorkspaceId("a").stream()
                .map(KnowledgeItem::id).collect(java.util.stream.Collectors.toSet()));
        client.deleteKnowledgeItemsBySourceId("a", "s");
        assertEquals(List.of("keep"), client.findKnowledgeItemsByWorkspaceId("a").stream().map(KnowledgeItem::id).toList());
        client.deleteKnowledgeItemsByWorkspaceId("a");
        assertTrue(client.findKnowledgeItemsByWorkspaceId("a").isEmpty());
        assertEquals(1, client.findKnowledgeItemsByWorkspaceId("b").size());
    }

    @Test
    void expandsFollowingChunksWithoutCrossingSectionsSourcesOrWorkspaces() throws Exception {
        var seed = sectionItem("s3", "a", "s", 3, "one");
        var secondSeed = sectionItem("s4", "a", "s", 4, "one");
        client.addKnowledgeItems(List.of(
                sectionItem("s1", "a", "s", 1, "one"),
                sectionItem("s2", "a", "s", 2, "one"), seed, secondSeed,
                sectionItem("s5", "a", "s", 5, "two"),
                sectionItem("other", "a", "other", 4, "one"),
                sectionItem("foreign", "b", "s", 4, "one")));
        // Round trip seeds verifies sectionId is actually persisted and read.
        var seeds = client.findKnowledgeItemsByWorkspaceId("a").stream()
                .filter(i -> i.id().equals("s3") || i.id().equals("s4")).toList();
        assertEquals(List.of("s3", "s4"), client.expandNeighbors("a", List.of(seed)).stream()
                .map(KnowledgeItem::id).toList());
        var expanded = client.expandNeighbors("a", seeds);
        assertEquals(List.of("s3", "s4"), expanded.stream().map(KnowledgeItem::id).toList());
        assertEquals(List.of("one", "one"), expanded.stream().map(KnowledgeItem::sectionId).toList());
    }

    @Test
    void expandsTwelveMatchesToTwentyFourChunks() throws Exception {
        var items = java.util.stream.IntStream.rangeClosed(1, 24)
                .mapToObj(i -> sectionItem("chunk" + i, "a", "s", i, "one")).toList();
        client.addKnowledgeItems(items);
        var seeds = items.stream().filter(i -> i.chunkSequence() % 2 == 1).toList();
        var expanded = client.expandNeighbors("a", seeds);
        assertEquals(items.stream().map(KnowledgeItem::id).toList(),
                expanded.stream().map(KnowledgeItem::id).toList());
    }

    @Test
    void preservesSeedRankingWhenAddingFollowingChunks() throws Exception {
        var items = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> sectionItem("rank" + i, "a", "s", i, "one")).toList();
        client.addKnowledgeItems(items);

        var expanded = client.expandNeighbors("a", List.of(items.get(3), items.get(1)));

        assertEquals(List.of("rank4", "rank5", "rank2", "rank3"),
                expanded.stream().map(KnowledgeItem::id).toList());
    }

    private KnowledgeItem sectionItem(String id, String workspace, String source, int sequence, String section) {
        return KnowledgeItem.builder().id(id).workspaceId(workspace).sourceId(source)
                .sourceType(KnowledgeSourceType.DOCUMENT).content(id).chunkId(id)
                .chunkSequence(sequence).sectionId(section).embedding(List.of(1f, 0f, 0f))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private KnowledgeItem item(String id, String workspace, String source, String content, List<Float> vector) {
        return KnowledgeItem.builder().id(id).workspaceId(workspace).sourceId(source)
                .sourceType(KnowledgeSourceType.DOCUMENT).sourceName("test.txt").content(content)
                .chunkId(id).chunkSequence(1).pageNumber(2).embedding(vector).embeddingDimensions(3)
                .startMilliseconds(1_250L).endMilliseconds(3_500L).speaker("Speaker 1")
                .embeddingModel("test").createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }
}
