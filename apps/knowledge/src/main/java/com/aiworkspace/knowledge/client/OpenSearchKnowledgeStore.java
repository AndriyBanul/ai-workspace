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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class OpenSearchKnowledgeStore {

    static final String INDEX = "knowledge-items-v3";

    final RestClient restClient;
    final ObjectMapper objectMapper;
    private final URI baseUri;
    private final KnowledgeEmbeddingProperties embeddingProperties;
    private volatile boolean indexChecked;

    OpenSearchKnowledgeStore(URI baseUri, RestClient restClient, ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties) {
        this.baseUri = baseUri;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.embeddingProperties = embeddingProperties;
    }

    void ensureIndex() throws IOException {
        if (indexChecked) return;
        synchronized (this) {
            if (indexChecked) return;
            if (!indexExists(indexUri())) createIndex();
            updateMapping();
            indexChecked = true;
        }
    }

    List<KnowledgeItem> searchItems(Map<String, Object> request, String errorMessage) throws IOException {
        return searchItems(request, searchUri(), errorMessage);
    }

    List<KnowledgeItem> searchItems(Map<String, Object> request, URI uri, String errorMessage) throws IOException {
        try {
            ResponseEntity<String> response = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request)).retrieve().toEntity(String.class);
            JsonNode hits = searchHits(response.getBody(), errorMessage);
            Map<String, KnowledgeItem> items = new LinkedHashMap<>();
            for (JsonNode hit : hits) {
                try {
                    KnowledgeItem item = itemFrom(hit.path("_source"), hit.path("_id").asText());
                    org.slf4j.LoggerFactory.getLogger(OpenSearchKnowledgeSearchReader.class).debug(
                            "retrieval_candidate workspaceId={} chunkId={} score={}",
                            item.workspaceId(), item.id(), hit.path("_score").asDouble());
                    items.putIfAbsent(item.id(), item);
                } catch (RuntimeException exception) {
                    throw invalidResponse(errorMessage, exception);
                }
            }
            return List.copyOf(items.values());
        } catch (RestClientResponseException exception) {
            throw responseException(errorMessage, exception);
        } catch (RestClientException exception) {
            throw clientException(errorMessage, exception);
        }
    }

    void validateQueryEmbedding(List<Float> queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.size() != embeddingProperties.dimensions()) {
            throw new IllegalArgumentException(
                    "Query embedding must contain " + embeddingProperties.dimensions() + " dimensions");
        }
    }

    URI searchUri() {
        return URI.create(baseUri + "/" + INDEX + "/_search");
    }

    URI bulkUri() {
        return URI.create(baseUri + "/_bulk?refresh=wait_for");
    }

    URI deleteByQueryUri() {
        return URI.create(baseUri + "/" + INDEX + "/_delete_by_query?refresh=true&conflicts=proceed");
    }

    UpstreamServiceException responseException(String message, RestClientResponseException exception) {
        return new UpstreamServiceException("OpenSearch",
                message + ": OpenSearch returned HTTP " + exception.getStatusCode().value(), exception);
    }

    UpstreamServiceException clientException(String message, RestClientException exception) {
        return new UpstreamServiceException("OpenSearch", message + ": OpenSearch is unavailable", exception);
    }

    UpstreamServiceException invalidResponse(String message, Exception exception) {
        return exception == null
                ? new UpstreamServiceException("OpenSearch", message + ": OpenSearch returned an invalid response")
                : new UpstreamServiceException(
                        "OpenSearch", message + ": OpenSearch returned an invalid response", exception);
    }

    private URI indexUri() {
        return URI.create(baseUri + "/" + INDEX);
    }

    private URI mappingUri() {
        return URI.create(baseUri + "/" + INDEX + "/_mapping");
    }

    private boolean indexExists(URI uri) throws IOException {
        try {
            restClient.head().uri(uri).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return false;
            throw responseException("Failed to check OpenSearch index", exception);
        } catch (RestClientException exception) {
            throw clientException("Failed to check OpenSearch index", exception);
        }
    }

    private void createIndex() throws IOException {
        try {
            restClient.put().uri(indexUri()).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of(
                            "settings", Map.of("index", Map.of("knn", true)),
                            "mappings", Map.of("properties", properties()))))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400
                    && exception.getResponseBodyAsString().contains("resource_already_exists_exception")) return;
            throw responseException("Failed to create knowledge items index", exception);
        } catch (RestClientException exception) {
            throw clientException("Failed to create knowledge items index", exception);
        }
    }

    private void updateMapping() throws IOException {
        try {
            restClient.put().uri(mappingUri()).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of("properties", properties())))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw responseException("Failed to update workspace knowledge mapping", exception);
        } catch (RestClientException exception) {
            throw clientException("Failed to update workspace knowledge mapping", exception);
        }
    }

    private Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("workspaceId", Map.of("type", "keyword"));
        properties.put("sourceType", Map.of("type", "keyword"));
        properties.put("sourceName", Map.of("type", "keyword"));
        properties.put("jobId", Map.of("type", "keyword"));
        properties.put("sourceId", Map.of("type", "keyword"));
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

    private JsonNode searchHits(String responseBody, String errorMessage) {
        try {
            JsonNode hits = objectMapper.readTree(responseBody == null ? "{}" : responseBody)
                    .path("hits").path("hits");
            if (!hits.isArray()) throw invalidResponse(errorMessage, null);
            return hits;
        } catch (IOException exception) {
            throw invalidResponse(errorMessage, exception);
        }
    }

    private KnowledgeItem itemFrom(JsonNode source, String fallbackId) {
        return KnowledgeItem.builder()
                .id(text(source, "id", fallbackId))
                .workspaceId(text(source, "workspaceId", ""))
                .source(KnowledgeItemSource.builder()
                        .type(sourceType(text(source, "sourceType", "documents")))
                        .name(text(source, "sourceName", null))
                        .jobId(text(source, "jobId", null))
                        .id(text(source, "sourceId", null))
                        .url(text(source, "sourceUrl", null))
                        .extractedAt(instant(source, "extractedAt"))
                        .parserVersion(text(source, "parserVersion", null))
                        .build())
                .content(text(source, "content", ""))
                .chunkMetadata(KnowledgeChunkMetadata.builder()
                        .id(text(source, "chunkId", null))
                        .sequence(integer(source, "chunkSequence"))
                        .sectionId(text(source, "sectionId", null))
                        .heading(text(source, "heading", null))
                        .pageNumber(integer(source, "pageNumber"))
                        .slideNumber(integer(source, "slideNumber"))
                        .sheetName(text(source, "sheetName", null))
                        .startMilliseconds(longValue(source, "startMilliseconds"))
                        .endMilliseconds(longValue(source, "endMilliseconds"))
                        .speaker(text(source, "speaker", null))
                        .build())
                .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                        floats(source, "embedding"),
                        text(source, "embeddingModel", null),
                        integer(source, "embeddingDimensions"),
                        text(source, "contentHash", null)))
                .createdAt(Instant.parse(text(source, "createdAt", Instant.EPOCH.toString())))
                .build();
    }

    private KnowledgeSourceType sourceType(String apiName) {
        for (KnowledgeSourceType type : KnowledgeSourceType.values()) if (type.apiName().equals(apiName)) return type;
        return KnowledgeSourceType.DOCUMENT;
    }

    private String text(JsonNode source, String field, String defaultValue) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asText();
    }

    private Instant instant(JsonNode source, String field) {
        String value = text(source, field, null);
        return value == null ? null : Instant.parse(value);
    }

    private Integer integer(JsonNode source, String field) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private Long longValue(JsonNode source, String field) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private List<Float> floats(JsonNode source, String field) {
        JsonNode values = source.path(field);
        if (!values.isArray()) return null;
        List<Float> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(value.floatValue()));
        return List.copyOf(result);
    }
}
