package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.OpenSearchHitResponse;
import com.aiworkspace.knowledge.models.OpenSearchKnowledgeDocument;
import com.aiworkspace.knowledge.models.OpenSearchSearchResponse;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
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
            List<OpenSearchHitResponse> hits = parseSearchHits(response.getBody(), errorMessage);
            Map<String, KnowledgeItem> items = new LinkedHashMap<>();
            for (OpenSearchHitResponse hit : hits) {
                try {
                    KnowledgeItem item = toKnowledgeItem(hit.source(), hit.id() == null ? "" : hit.id());
                    org.slf4j.LoggerFactory.getLogger(OpenSearchKnowledgeSearchReader.class).debug(
                            "retrieval_candidate workspaceId={} chunkId={} score={}",
                            item.workspaceId(), item.id(), hit.score());
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

    private List<OpenSearchHitResponse> parseSearchHits(String responseBody, String errorMessage) {
        try {
            OpenSearchSearchResponse response = objectMapper.readValue(
                    responseBody == null ? "" : responseBody, OpenSearchSearchResponse.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                throw createInvalidResponseException(errorMessage, null);
            }
            return response.hits().hits();
        } catch (IOException exception) {
            throw createInvalidResponseException(errorMessage, exception);
        }
    }

    private KnowledgeItem toKnowledgeItem(OpenSearchKnowledgeDocument document, String fallbackId) {
        OpenSearchKnowledgeDocument source = document == null ? OpenSearchKnowledgeDocument.builder().build() : document;
        return KnowledgeItem.builder()
                .id(source.id() == null ? fallbackId : source.id())
                .workspaceId(source.workspaceId() == null ? "" : source.workspaceId())
                .source(KnowledgeItemSource.builder()
                        .type(resolveSourceType(source.sourceType()))
                        .name(source.sourceName())
                        .jobId(source.jobId())
                        .id(source.sourceId())
                        .url(source.sourceUrl())
                        .extractedAt(parseOptionalInstant(source.extractedAt()))
                        .parserVersion(source.parserVersion())
                        .generation(source.generation())
                        .build())
                .content(source.content() == null ? "" : source.content())
                .chunkMetadata(KnowledgeChunkMetadata.builder()
                        .id(source.chunkId())
                        .sequence(source.chunkSequence())
                        .sectionId(source.sectionId())
                        .heading(source.heading())
                        .pageNumber(source.pageNumber())
                        .slideNumber(source.slideNumber())
                        .sheetName(source.sheetName())
                        .startMilliseconds(source.startMilliseconds())
                        .endMilliseconds(source.endMilliseconds())
                        .speaker(source.speaker())
                        .build())
                .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                        source.embedding(), source.embeddingModel(), source.embeddingDimensions(), source.contentHash()))
                .createdAt(source.createdAt() == null ? Instant.EPOCH : Instant.parse(source.createdAt()))
                .build();
    }

    private KnowledgeSourceType resolveSourceType(String apiName) {
        for (KnowledgeSourceType type : KnowledgeSourceType.values()) if (type.apiName().equals(apiName)) return type;
        return KnowledgeSourceType.DOCUMENT;
    }

    private Instant parseOptionalInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
