package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.services.ReciprocalRankFusion;
import com.aiworkspace.knowledge.services.SourceIndexManifestService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OpenSearchKnowledgeSearchReader {

    private final OpenSearchKnowledgeStore store;
    private final int headingWeight;
    private final SearchTelemetry telemetry;
    private final SourceIndexManifestService manifests;

    OpenSearchKnowledgeSearchReader(OpenSearchKnowledgeStore store, int headingWeight, SearchTelemetry telemetry) {
        this(store, headingWeight, telemetry, null);
    }

    OpenSearchKnowledgeSearchReader(OpenSearchKnowledgeStore store, int headingWeight, SearchTelemetry telemetry,
            SourceIndexManifestService manifests) {
        if (headingWeight <= 0) throw new IllegalArgumentException("Heading weight must be positive");
        this.store = store;
        this.headingWeight = headingWeight;
        this.telemetry = telemetry == null ? SearchTelemetry.NOOP : telemetry;
        this.manifests = manifests;
    }

    Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException {
        List<KnowledgeItem> items = findByWorkspaceIdItems(workspaceId);
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(new WorkspaceKnowledge(workspaceId,
                joinedContent(items, KnowledgeSourceType.DOCUMENT), joinedContent(items, KnowledgeSourceType.AUDIO),
                joinedContent(items, KnowledgeSourceType.VIDEO), joinedContent(items, KnowledgeSourceType.IMAGE)));
    }

    List<KnowledgeItem> findByWorkspaceIdItems(String workspaceId) throws IOException {
        store.ensureIndex();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", 1000);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("bool", Map.of("filter", sourceFilters(workspaceId))));
        request.put("sort", List.of(
                Map.of("createdAt", Map.of("order", "asc")),
                Map.of("sourceId", Map.of("order", "asc", "missing", "_last")),
                Map.of("chunkSequence", Map.of("order", "asc", "missing", "_last")),
                Map.of("_id", Map.of("order", "asc"))));
        return store.searchItems(request, "Failed to fetch workspace knowledge items");
    }

    List<KnowledgeItem> search(String workspaceId, String query, int limit) throws IOException {
        store.ensureIndex();
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", sourceFilters(workspaceId));
        bool.put("must", List.of(Map.of("multi_match", Map.of(
                "query", query, "fields", List.of("content", "heading^" + headingWeight, "speaker^2")))));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", limit);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("bool", bool));
        return telemetry.measure("search.bm25",
                () -> store.searchItems(request, "Failed to search workspace knowledge items"));
    }

    List<KnowledgeItem> hybridSearch(String workspaceId, String query, List<Float> embedding, int limit,
            int candidateLimit, int rrfRankConstant) throws IOException {
        store.ensureIndex();
        store.validateQueryEmbedding(embedding);
        int effectiveLimit = Math.max(limit, candidateLimit);
        return ReciprocalRankFusion.fuse(List.of(
                search(workspaceId, query, effectiveLimit),
                vectorCandidates(workspaceId, embedding, effectiveLimit, effectiveLimit)), limit, rrfRankConstant);
    }

    List<KnowledgeItem> vectorSearch(String workspaceId, List<Float> embedding, int limit, int candidateLimit)
            throws IOException {
        int effectiveLimit = Math.max(limit, candidateLimit);
        return vectorCandidates(workspaceId, embedding, effectiveLimit, limit);
    }

    List<KnowledgeItem> expandNeighbors(String workspaceId, List<KnowledgeItem> matches) throws IOException {
        if (matches.size() > 12) throw new IllegalArgumentException("At most twelve search matches can be expanded");
        if (matches.isEmpty()) return List.of();
        var clauses = new ArrayList<Map<String, Object>>();
        var result = new LinkedHashMap<String, KnowledgeItem>();
        for (var match : matches) {
            if (!workspaceId.equals(match.workspaceId())) throw new IllegalArgumentException("Workspace mismatch");
            result.put(match.id(), match);
            if (match.sourceId() == null || match.sectionId() == null || match.chunkSequence() == null) continue;
            clauses.add(Map.of("bool", Map.of("filter", List.of(
                    Map.of("term", Map.of("sourceId", match.sourceId())),
                    Map.of("term", Map.of("sectionId", match.sectionId())),
                    Map.of("range", Map.of("chunkSequence", Map.of(
                            "gte", match.chunkSequence(), "lte", match.chunkSequence() + 1)))))));
        }
        if (!clauses.isEmpty()) {
            store.ensureIndex();
            var request = new LinkedHashMap<String, Object>();
            request.put("size", 24);
            request.put("_source", Map.of("excludes", List.of("embedding")));
            request.put("query", Map.of("bool", Map.of(
                    "filter", sourceFilters(workspaceId),
                    "should", clauses, "minimum_should_match", 1)));
            var neighbors = new LinkedHashMap<String, KnowledgeItem>();
            for (var item : store.searchItems(request, "Failed to fetch neighboring chunks")) {
                if (item.sourceId() != null && item.sectionId() != null && item.chunkSequence() != null) {
                    neighbors.put(neighborKey(item.sourceId(), item.sectionId(), item.chunkSequence()), item);
                }
            }
            result.clear();
            for (var match : matches) {
                result.put(match.id(), match);
                if (match.sourceId() != null && match.sectionId() != null && match.chunkSequence() != null) {
                    var following = neighbors.get(neighborKey(
                            match.sourceId(), match.sectionId(), match.chunkSequence() + 1));
                    if (following != null) result.put(following.id(), following);
                }
            }
        }
        return List.copyOf(result.values());
    }

    private List<KnowledgeItem> vectorCandidates(String workspaceId, List<Float> embedding, int candidateLimit,
            int resultLimit) throws IOException {
        store.ensureIndex();
        store.validateQueryEmbedding(embedding);
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("vector", embedding);
        knn.put("k", candidateLimit);
        knn.put("filter", Map.of("bool", Map.of("filter", sourceFilters(workspaceId))));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", candidateLimit);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("knn", Map.of("embedding", knn)));
        return telemetry.measure("search.vector",
                () -> store.searchItems(request, store.searchUri(), "Failed to run vector search"))
                .stream().limit(resultLimit).toList();
    }

    private String neighborKey(String sourceId, String sectionId, int sequence) {
        return sourceId + "\u0000" + sectionId + "\u0000" + sequence;
    }

    private List<Map<String, Object>> sourceFilters(String workspaceId) {
        Map<String, Object> workspaceFilter = Map.of("term", Map.of("workspaceId", workspaceId));
        if (manifests == null) return List.of(workspaceFilter);
        var active = manifests.findByWorkspaceId(workspaceId);
        Map<String, Object> legacy = Map.of("bool", Map.of("must_not", List.of(
                Map.of("exists", Map.of("field", "sourceGeneration")),
                Map.of("terms", Map.of("sourceId", active.stream().map(item -> item.getSourceId()).toList())))));
        if (active.isEmpty()) {
            return List.of(workspaceFilter, Map.of("bool", Map.of("must_not", List.of(
                    Map.of("exists", Map.of("field", "sourceGeneration"))))));
        }
        Map<String, Object> published = Map.of("terms", Map.of("sourceGeneration", active.stream()
                .map(item -> item.getSourceId() + ":" + item.getActiveGeneration()).toList()));
        return List.of(workspaceFilter, Map.of("bool", Map.of(
                "should", List.of(published, legacy), "minimum_should_match", 1)));
    }

    private String joinedContent(List<KnowledgeItem> items, KnowledgeSourceType type) {
        return items.stream().filter(item -> item.sourceType() == type).map(KnowledgeItem::content)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n\n" + right).orElse(null);
    }
}
