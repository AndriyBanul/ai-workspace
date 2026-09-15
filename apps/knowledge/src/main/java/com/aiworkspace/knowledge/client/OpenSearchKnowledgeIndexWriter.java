package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class OpenSearchKnowledgeIndexWriter {

    private final OpenSearchKnowledgeStore store;

    OpenSearchKnowledgeIndexWriter(OpenSearchKnowledgeStore store) {
        this.store = store;
    }

    void add(List<KnowledgeItem> items) throws IOException {
        if (items == null || items.isEmpty()) return;
        store.ensureIndex();
        try {
            StringBuilder body = new StringBuilder();
            for (KnowledgeItem item : items) {
                body.append(store.objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", OpenSearchKnowledgeStore.INDEX, "_id", item.id())))).append('\n');
                body.append(store.objectMapper.writeValueAsString(document(item))).append('\n');
            }
            ResponseEntity<String> response = store.restClient.post().uri(store.bulkUri())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body.toString().getBytes(StandardCharsets.UTF_8)).retrieve().toEntity(String.class);
            JsonNode responseBody = store.objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            if (responseBody.path("errors").asBoolean(true)) {
                throw store.invalidResponse("Failed to add workspace knowledge items", null);
            }
        } catch (RestClientResponseException exception) {
            throw store.responseException("Failed to add workspace knowledge items", exception);
        } catch (RestClientException exception) {
            throw store.clientException("Failed to add workspace knowledge items", exception);
        }
    }

    void replace(String workspaceId, String sourceId, List<KnowledgeItem> items) throws IOException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Replacement knowledge items must not be empty");
        }
        for (KnowledgeItem item : items) {
            if (!workspaceId.equals(item.workspaceId()) || !sourceId.equals(item.sourceId())) {
                throw new IllegalArgumentException("Replacement knowledge items must belong to the requested source");
            }
        }
        add(items);
        deleteSourceExcept(workspaceId, sourceId, items.stream().map(KnowledgeItem::id).toList());
    }

    void deleteSource(String workspaceId, String sourceId) throws IOException {
        deleteSourceExcept(workspaceId, sourceId, List.of());
    }

    void deleteWorkspace(String workspaceId) throws IOException {
        executeDelete(Map.of("term", Map.of("workspaceId", workspaceId)));
    }

    private void deleteSourceExcept(String workspaceId, String sourceId, List<String> retainedIds)
            throws IOException {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", List.of(Map.of("term", Map.of("workspaceId", workspaceId)),
                Map.of("term", Map.of("sourceId", sourceId))));
        if (!retainedIds.isEmpty()) bool.put("must_not", List.of(Map.of("ids", Map.of("values", retainedIds))));
        executeDelete(Map.of("bool", bool));
    }

    private void executeDelete(Map<String, Object> query) throws IOException {
        store.ensureIndex();
        try {
            ResponseEntity<String> response = store.restClient.post().uri(store.deleteByQueryUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(store.objectMapper.writeValueAsString(Map.of("query", query)))
                    .retrieve().toEntity(String.class);
            validateDeleteResponse(response.getBody());
        } catch (RestClientResponseException exception) {
            throw store.responseException("Failed to delete source knowledge", exception);
        } catch (RestClientException exception) {
            throw store.clientException("Failed to delete source knowledge", exception);
        }
    }

    private void validateDeleteResponse(String responseBody) {
        JsonNode response;
        try {
            response = store.objectMapper.readTree(responseBody == null ? "{}" : responseBody);
        } catch (IOException exception) {
            throw store.invalidResponse("Failed to delete source knowledge", exception);
        }
        if (response.path("timed_out").asBoolean(false) || !response.path("failures").isArray()
                || !response.path("failures").isEmpty()) {
            throw store.invalidResponse("Failed to delete source knowledge", null);
        }
    }

    private Map<String, Object> document(KnowledgeItem item) {
        KnowledgeItemSource source = item.source();
        KnowledgeChunkMetadata chunk = item.chunkMetadata();
        KnowledgeEmbeddingMetadata embedding = item.embeddingMetadata();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", item.id());
        document.put("workspaceId", item.workspaceId());
        document.put("sourceType", source == null || source.type() == null ? null : source.type().apiName());
        document.put("sourceName", source == null ? null : source.name());
        document.put("jobId", source == null ? null : source.jobId());
        document.put("sourceId", source == null ? null : source.id());
        document.put("sourceUrl", source == null ? null : source.url());
        document.put("content", item.content());
        document.put("extractedAt", source == null || source.extractedAt() == null
                ? null : source.extractedAt().toString());
        document.put("parserVersion", source == null ? null : source.parserVersion());
        document.put("chunkId", chunk == null ? null : chunk.id());
        document.put("chunkSequence", chunk == null ? null : chunk.sequence());
        document.put("sectionId", chunk == null ? null : chunk.sectionId());
        document.put("heading", chunk == null ? null : chunk.heading());
        document.put("pageNumber", chunk == null ? null : chunk.pageNumber());
        document.put("slideNumber", chunk == null ? null : chunk.slideNumber());
        document.put("sheetName", chunk == null ? null : chunk.sheetName());
        document.put("startMilliseconds", chunk == null ? null : chunk.startMilliseconds());
        document.put("endMilliseconds", chunk == null ? null : chunk.endMilliseconds());
        document.put("speaker", chunk == null ? null : chunk.speaker());
        document.put("embedding", embedding == null ? null : embedding.vector());
        document.put("embeddingModel", embedding == null ? null : embedding.model());
        document.put("embeddingDimensions", embedding == null ? null : embedding.dimensions());
        document.put("contentHash", embedding == null ? null : embedding.contentHash());
        document.put("createdAt", item.createdAt().toString());
        return document;
    }
}
