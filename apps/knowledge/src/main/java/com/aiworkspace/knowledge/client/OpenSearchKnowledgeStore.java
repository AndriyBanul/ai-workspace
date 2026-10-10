package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class OpenSearchKnowledgeStore {

    static final String INDEX = "knowledge-items-v3";

    private final URI baseUri;
    final RestClient restClient;
    final ObjectMapper objectMapper;
    private final KnowledgeEmbeddingProperties embeddingProperties;
    private volatile boolean indexChecked;

    void ensureIndex() throws IOException {
        if (indexChecked) return;
        synchronized (this) {
            if (indexChecked) return;
            if (!indexExists(buildIndexUri())) createIndex();
            updateMapping();
            indexChecked = true;
        }
    }

    List<KnowledgeItem> searchItems(Map<String, Object> request, String errorMessage) throws IOException {
        return searchItems(request, buildSearchUri(), errorMessage);
    }

    List<KnowledgeItem> searchItems(Map<String, Object> request, URI uri, String errorMessage) throws IOException {
        try {
            ResponseEntity<String> response = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request)).retrieve().toEntity(String.class);
            JsonNode hits = parseSearchHits(response.getBody(), errorMessage);
            Map<String, KnowledgeItem> items = new LinkedHashMap<>();
            for (JsonNode hit : hits) {
                try {
                    KnowledgeItem item = toKnowledgeItem(hit.path("_source"), hit.path("_id").asText());
                    org.slf4j.LoggerFactory.getLogger(OpenSearchKnowledgeSearchReader.class).debug(
                            "retrieval_candidate workspaceId={} chunkId={} score={}",
                            item.workspaceId(), item.id(), hit.path("_score").asDouble());
                    items.putIfAbsent(item.id(), item);
                } catch (RuntimeException exception) {
                    throw createInvalidResponseException(errorMessage, exception);
                }
            }
            return List.copyOf(items.values());
        } catch (RestClientResponseException exception) {
            throw createResponseException(errorMessage, exception);
        } catch (RestClientException exception) {
            throw createClientException(errorMessage, exception);
        }
    }

    void validateQueryEmbedding(List<Float> queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.size() != embeddingProperties.dimensions()) {
            throw new IllegalArgumentException(
                    "Query embedding must contain " + embeddingProperties.dimensions() + " dimensions");
        }
    }

    URI buildSearchUri() {
        return URI.create(baseUri + "/" + INDEX + "/_search");
    }

    URI buildBulkUri() {
        return URI.create(baseUri + "/_bulk?refresh=wait_for");
    }

    URI buildCountUri() {
        return URI.create(baseUri + "/" + INDEX + "/_count");
    }

    URI buildDeleteByQueryUri() {
        return URI.create(baseUri + "/" + INDEX + "/_delete_by_query?refresh=true&conflicts=proceed");
    }

    UpstreamServiceException createResponseException(String message, RestClientResponseException exception) {
        return new UpstreamServiceException("OpenSearch",
                message + ": OpenSearch returned HTTP " + exception.getStatusCode().value(), exception);
    }

    UpstreamServiceException createClientException(String message, RestClientException exception) {
        return new UpstreamServiceException("OpenSearch", message + ": OpenSearch is unavailable", exception);
    }

    UpstreamServiceException createInvalidResponseException(String message, Exception exception) {
        return exception == null
                ? new UpstreamServiceException("OpenSearch", message + ": OpenSearch returned an invalid response")
                : new UpstreamServiceException(
                        "OpenSearch", message + ": OpenSearch returned an invalid response", exception);
    }

    private URI buildIndexUri() {
        return URI.create(baseUri + "/" + INDEX);
    }

    private URI buildMappingUri() {
        return URI.create(baseUri + "/" + INDEX + "/_mapping");
    }

    private boolean indexExists(URI uri) throws IOException {
        try {
            restClient.head().uri(uri).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return false;
            throw createResponseException("Failed to check OpenSearch index", exception);
        } catch (RestClientException exception) {
            throw createClientException("Failed to check OpenSearch index", exception);
        }
    }

    private void createIndex() throws IOException {
        try {
            restClient.put().uri(buildIndexUri()).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of(
                            "settings", Map.of("index", Map.of("knn", true)),
                            "mappings", Map.of("properties", buildMappingProperties()))))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400
                    && exception.getResponseBodyAsString().contains("resource_already_exists_exception")) return;
            throw createResponseException("Failed to create knowledge items index", exception);
        } catch (RestClientException exception) {
            throw createClientException("Failed to create knowledge items index", exception);
        }
    }

    private void updateMapping() throws IOException {
        try {
            restClient.put().uri(buildMappingUri()).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of("properties", buildMappingProperties())))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw createResponseException("Failed to update workspace knowledge mapping", exception);
        } catch (RestClientException exception) {
            throw createClientException("Failed to update workspace knowledge mapping", exception);
        }
    }

    private Map<String, Object> buildMappingProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("workspaceId", Map.of("type", "keyword"));
        properties.put("sourceType", Map.of("type", "keyword"));
        properties.put("sourceName", Map.of("type", "keyword"));
        properties.put("jobId", Map.of("type", "keyword"));
        properties.put("sourceId", Map.of("type", "keyword"));
        properties.put("sourceGeneration", Map.of("type", "keyword"));
        properties.put("sourceUrl", Map.of("type", "keyword", "ignore_above", 2048));
        properties.put("content", Map.of("type", "text"));
        properties.put("extractedAt", Map.of("type", "date"));
        properties.put("parserVersion", Map.of("type", "keyword"));
        properties.put("chunkId", Map.of("type", "keyword"));
        properties.put("chunkSequence", Map.of("type", "integer"));
        properties.put("sectionId", Map.of("type", "keyword"));
        properties.put("heading", Map.of("type", "text"));
        properties.put("pageNumber", Map.of("type", "integer"));
        properties.put("slideNumber", Map.of("type", "integer"));
        properties.put("sheetName", Map.of("type", "keyword"));
        properties.put("startMilliseconds", Map.of("type", "long"));
        properties.put("endMilliseconds", Map.of("type", "long"));
        properties.put("speaker", Map.of("type", "keyword"));
        properties.put("embedding", Map.of("type", "knn_vector", "dimension", embeddingProperties.dimensions(),
                "method", Map.of("name", "hnsw", "space_type", "cosinesimil", "engine", "lucene",
                        "parameters", Map.of("ef_construction", 128, "m", 16))));
        properties.put("embeddingModel", Map.of("type", "keyword"));
        properties.put("embeddingDimensions", Map.of("type", "integer"));
        properties.put("contentHash", Map.of("type", "keyword"));
        properties.put("createdAt", Map.of("type", "date"));
        return properties;
    }

    private JsonNode parseSearchHits(String responseBody, String errorMessage) {
        try {
            JsonNode hits = objectMapper.readTree(responseBody == null ? "{}" : responseBody)
                    .path("hits").path("hits");
            if (!hits.isArray()) throw createInvalidResponseException(errorMessage, null);
            return hits;
        } catch (IOException exception) {
            throw createInvalidResponseException(errorMessage, exception);
        }
    }

    private KnowledgeItem toKnowledgeItem(JsonNode source, String fallbackId) {
        return KnowledgeItem.builder()
                .id(readText(source, "id", fallbackId))
                .workspaceId(readText(source, "workspaceId", ""))
                .source(KnowledgeItemSource.builder()
                        .type(resolveSourceType(readText(source, "sourceType", "documents")))
                        .name(readText(source, "sourceName", null))
                        .jobId(readText(source, "jobId", null))
                        .id(readText(source, "sourceId", null))
                        .url(readText(source, "sourceUrl", null))
                        .extractedAt(readInstant(source, "extractedAt"))
                        .parserVersion(readText(source, "parserVersion", null))
                        .generation(readText(source, "generation", null))
                        .build())
                .content(readText(source, "content", ""))
                .chunkMetadata(KnowledgeChunkMetadata.builder()
                        .id(readText(source, "chunkId", null))
                        .sequence(readInteger(source, "chunkSequence"))
                        .sectionId(readText(source, "sectionId", null))
                        .heading(readText(source, "heading", null))
                        .pageNumber(readInteger(source, "pageNumber"))
                        .slideNumber(readInteger(source, "slideNumber"))
                        .sheetName(readText(source, "sheetName", null))
                        .startMilliseconds(readLong(source, "startMilliseconds"))
                        .endMilliseconds(readLong(source, "endMilliseconds"))
                        .speaker(readText(source, "speaker", null))
                        .build())
                .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                        readFloats(source, "embedding"),
                        readText(source, "embeddingModel", null),
                        readInteger(source, "embeddingDimensions"),
                        readText(source, "contentHash", null)))
                .createdAt(Instant.parse(readText(source, "createdAt", Instant.EPOCH.toString())))
                .build();
    }

    private KnowledgeSourceType resolveSourceType(String apiName) {
        for (KnowledgeSourceType type : KnowledgeSourceType.values()) if (type.apiName().equals(apiName)) return type;
        return KnowledgeSourceType.DOCUMENT;
    }

    private String readText(JsonNode source, String field, String defaultValue) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asText();
    }

    private Instant readInstant(JsonNode source, String field) {
        String value = readText(source, field, null);
        return value == null ? null : Instant.parse(value);
    }

    private Integer readInteger(JsonNode source, String field) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private Long readLong(JsonNode source, String field) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private List<Float> readFloats(JsonNode source, String field) {
        JsonNode values = source.path(field);
        if (!values.isArray()) return null;
        List<Float> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(value.floatValue()));
        return List.copyOf(result);
    }
}
