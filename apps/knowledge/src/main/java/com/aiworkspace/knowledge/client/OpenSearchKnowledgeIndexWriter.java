package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.OpenSearchBulkResponse;
import com.aiworkspace.knowledge.models.OpenSearchCountResponse;
import com.aiworkspace.knowledge.models.OpenSearchDeleteResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class OpenSearchKnowledgeIndexWriter {

    private final OpenSearchKnowledgeStore store;

    void add(List<KnowledgeItem> items) throws IOException {
        if (items == null || items.isEmpty()) return;
        store.ensureIndex();
        try {
            StringBuilder body = new StringBuilder();
            for (KnowledgeItem item : items) {
                body.append(store.objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", OpenSearchKnowledgeStore.INDEX, "_id", item.id())))).append('\n');
                body.append(store.objectMapper.writeValueAsString(toIndexDocument(item))).append('\n');
            }
            ResponseEntity<String> response = store.restClient.post().uri(store.buildBulkUri())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body.toString().getBytes(StandardCharsets.UTF_8)).retrieve().toEntity(String.class);
            OpenSearchBulkResponse responseBody = parseResponse(
                    response.getBody(), OpenSearchBulkResponse.class, "Failed to add workspace knowledge items"
            );
            if (!Boolean.FALSE.equals(responseBody.errors())) {
                throw store.createInvalidResponseException("Failed to add workspace knowledge items", null);
            }
        } catch (RestClientResponseException exception) {
            throw store.createResponseException("Failed to add workspace knowledge items", exception);
        } catch (RestClientException exception) {
            throw store.createClientException("Failed to add workspace knowledge items", exception);
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

    void stage(String workspaceId, String sourceId, String generation, List<KnowledgeItem> items)
            throws IOException {
        if (items == null || items.isEmpty() || items.stream().anyMatch(item ->
                !workspaceId.equals(item.workspaceId()) || !sourceId.equals(item.sourceId())
                        || !generation.equals(item.generation()))) {
            throw new IllegalArgumentException("Staged items must belong to one source generation");
        }
        add(items);
        if (countSourceGeneration(workspaceId, sourceId, generation) != items.size()) {
            throw store.createInvalidResponseException("Incomplete source generation after bulk indexing", null);
        }
    }

    int countSourceGeneration(String workspaceId, String sourceId, String generation) throws IOException {
        store.ensureIndex();
        try {
            ResponseEntity<String> response = store.restClient.post().uri(store.buildCountUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(store.objectMapper.writeValueAsString(Map.of("query", buildSourceGenerationQuery(
                            workspaceId, sourceId, generation))))
                    .retrieve().toEntity(String.class);
            OpenSearchCountResponse body = parseResponse(
                    response.getBody(), OpenSearchCountResponse.class, "Failed to count indexed source generation"
            );
            if (body.count() == null) {
                throw store.createInvalidResponseException("Failed to count indexed source generation", null);
            }
            return body.count();
        } catch (RestClientResponseException exception) {
            throw store.createResponseException("Failed to count indexed source generation", exception);
        } catch (RestClientException exception) {
            throw store.createClientException("Failed to count indexed source generation", exception);
        }
    }

    void pruneSourceGenerations(String workspaceId, String sourceId, String activeGeneration) throws IOException {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", List.of(Map.of("term", Map.of("workspaceId", workspaceId)),
                Map.of("term", Map.of("sourceId", sourceId))));
        bool.put("must_not", List.of(Map.of("term", Map.of("sourceGeneration", sourceId + ":" + activeGeneration))));
        executeDelete(Map.of("bool", bool));
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

    private Map<String, Object> buildSourceGenerationQuery(String workspaceId, String sourceId, String generation) {
        return Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("workspaceId", workspaceId)),
                Map.of("term", Map.of("sourceId", sourceId)),
                Map.of("term", Map.of("sourceGeneration", sourceId + ":" + generation)))));
    }

    private void executeDelete(Map<String, Object> query) throws IOException {
        store.ensureIndex();
        try {
            ResponseEntity<String> response = store.restClient.post().uri(store.buildDeleteByQueryUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(store.objectMapper.writeValueAsString(Map.of("query", query)))
                    .retrieve().toEntity(String.class);
            validateDeleteResponse(response.getBody());
        } catch (RestClientResponseException exception) {
            throw store.createResponseException("Failed to delete source knowledge", exception);
        } catch (RestClientException exception) {
            throw store.createClientException("Failed to delete source knowledge", exception);
        }
    }

    private void validateDeleteResponse(String responseBody) {
        OpenSearchDeleteResponse response = parseResponse(
                responseBody, OpenSearchDeleteResponse.class, "Failed to delete source knowledge"
        );
        if (Boolean.TRUE.equals(response.timedOut()) || response.failures() == null || !response.failures().isEmpty()) {
            throw store.createInvalidResponseException("Failed to delete source knowledge", null);
        }
    }

    private <T> T parseResponse(String responseBody, Class<T> responseType, String errorMessage) {
        try {
            T response = store.objectMapper.readValue(responseBody == null ? "" : responseBody, responseType);
            if (response == null) {
                throw store.createInvalidResponseException(errorMessage, null);
            }
            return response;
        } catch (IOException exception) {
            throw store.createInvalidResponseException(errorMessage, exception);
        }
    }

    private Map<String, Object> toIndexDocument(KnowledgeItem item) {
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
        document.put("sourceGeneration", source == null || source.id() == null || source.generation() == null
                ? null : source.id() + ":" + source.generation());
        document.put("generation", source == null ? null : source.generation());
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
