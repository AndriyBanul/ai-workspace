package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenSearchKnowledgeClient implements KnowledgeRepository {

    private static final String KNOWLEDGE_ITEMS_INDEX = "knowledge-items";

    private final URI baseUri;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeEmbeddingProperties embeddingProperties;
    private volatile boolean knowledgeItemsIndexChecked;

    @Autowired
    public OpenSearchKnowledgeClient(
            @Value("${ai-workspace.opensearch.base-url:http://localhost:9200}") String baseUrl,
            RestClient restClient,
            ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                restClient,
                objectMapper,
                embeddingProperties
        );
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this(baseUri, restClient, objectMapper, new KnowledgeEmbeddingProperties(null, null, null, null, null, null));
    }

    OpenSearchKnowledgeClient(
            URI baseUri,
            RestClient restClient,
            ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties
    ) {
        this.baseUri = baseUri;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException {
        List<KnowledgeItem> items = findKnowledgeItemsByWorkspaceId(workspaceId);
        if (items.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new WorkspaceKnowledge(
                workspaceId,
                joinedContent(items, KnowledgeSourceType.DOCUMENT),
                joinedContent(items, KnowledgeSourceType.AUDIO),
                joinedContent(items, KnowledgeSourceType.VIDEO),
                joinedContent(items, KnowledgeSourceType.IMAGE)
        ));
    }

    @Override
    public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException {
        ensureKnowledgeItemsIndex();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", 1000);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("term", Map.of("workspaceId", workspaceId)));
        request.put("sort", List.of(
                Map.of("createdAt", Map.of("order", "asc")),
                Map.of("sourceId", Map.of("order", "asc", "missing", "_last")),
                Map.of("chunkSequence", Map.of("order", "asc", "missing", "_last")),
                Map.of("_id", Map.of("order", "asc"))
        ));

        return searchItems(request, "Failed to fetch workspace knowledge items");
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) throws IOException {
        ensureKnowledgeItemsIndex();

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", List.of(Map.of("term", Map.of("workspaceId", workspaceId))));
        bool.put("must", List.of(Map.of("multi_match", Map.of(
                "query", query,
                "fields", List.of("content", "heading^2")
        ))));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", limit);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("bool", bool));

        return searchItems(request, "Failed to search workspace knowledge items");
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItems(
            String workspaceId,
            String query,
            List<Float> queryEmbedding,
            int limit,
            int candidateLimit,
            int rrfRankConstant
    ) throws IOException {
        ensureKnowledgeItemsIndex();
        validateQueryEmbedding(queryEmbedding);
        int effectiveCandidateLimit = Math.max(limit, candidateLimit);

        List<KnowledgeItem> lexicalResults = searchKnowledgeItems(workspaceId, query, effectiveCandidateLimit);
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("vector", queryEmbedding);
        knn.put("k", effectiveCandidateLimit);
        knn.put("filter", Map.of("term", Map.of("workspaceId", workspaceId)));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", effectiveCandidateLimit);
        request.put("_source", Map.of("excludes", List.of("embedding")));
        request.put("query", Map.of("knn", Map.of("embedding", knn)));
        List<KnowledgeItem> vectorResults = searchItems(
                request,
                vectorKnowledgeItemsSearchUri(),
                "Failed to run vector search"
        );

        return reciprocalRankFusion(lexicalResults, vectorResults, limit, rrfRankConstant);
    }

    @Override
    public void addKnowledgeItem(KnowledgeItem item) throws IOException {
        addKnowledgeItems(List.of(item));
    }

    @Override
    public void addKnowledgeItems(List<KnowledgeItem> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return;
        }
        ensureKnowledgeItemsIndex();

        try {
            StringBuilder body = new StringBuilder();
            for (KnowledgeItem item : items) {
                body.append(objectMapper.writeValueAsString(Map.of(
                        "index",
                        Map.of("_index", KNOWLEDGE_ITEMS_INDEX, "_id", item.id())
                ))).append('\n');
                body.append(objectMapper.writeValueAsString(knowledgeItemDocument(item))).append('\n');
            }

            ResponseEntity<String> response = restClient.post()
                    .uri(knowledgeItemsBulkUri())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body.toString())
                    .retrieve()
                    .toEntity(String.class);
            JsonNode responseBody = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            if (responseBody.path("errors").asBoolean(true)) {
                throw openSearchInvalidResponseException("Failed to add workspace knowledge items", null);
            }
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException("Failed to add workspace knowledge items", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to add workspace knowledge items", exception);
        }
    }

    @Override
    public void replaceKnowledgeItems(String workspaceId, String sourceId, List<KnowledgeItem> items)
            throws IOException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Replacement knowledge items must not be empty");
        }
        for (KnowledgeItem item : items) {
            if (!workspaceId.equals(item.workspaceId()) || !sourceId.equals(item.sourceId())) {
                throw new IllegalArgumentException("Replacement knowledge items must belong to the requested source");
            }
        }

        addKnowledgeItems(items);
        deleteKnowledgeItemsBySourceIdExcept(workspaceId, sourceId, items.stream().map(KnowledgeItem::id).toList());
    }

    @Override
    public void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) throws IOException {
        deleteKnowledgeItemsBySourceIdExcept(workspaceId, sourceId, List.of());
    }

    @Override
    public void deleteKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException {
        executeDeleteByQuery(Map.of("term", Map.of("workspaceId", workspaceId)));
    }

    private void deleteKnowledgeItemsBySourceIdExcept(
            String workspaceId,
            String sourceId,
            List<String> retainedItemIds
    ) throws IOException {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", List.of(
                Map.of("term", Map.of("workspaceId", workspaceId)),
                Map.of("term", Map.of("sourceId", sourceId))
        ));
        if (!retainedItemIds.isEmpty()) {
            bool.put("must_not", List.of(Map.of("ids", Map.of("values", retainedItemIds))));
        }

        executeDeleteByQuery(Map.of("bool", bool));
    }

    private void executeDeleteByQuery(Map<String, Object> query) throws IOException {
        ensureKnowledgeItemsIndex();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(knowledgeItemsDeleteByQueryUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of("query", query)))
                    .retrieve()
                    .toEntity(String.class);
            validateDeleteResponse(response.getBody());
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException("Failed to delete source knowledge", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to delete source knowledge", exception);
        }
    }

    private void validateDeleteResponse(String responseBody) {
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
        } catch (IOException exception) {
            throw openSearchInvalidResponseException("Failed to delete source knowledge", exception);
        }
        if (response.path("timed_out").asBoolean(false)
                || !response.path("failures").isArray()
                || !response.path("failures").isEmpty()) {
            throw openSearchInvalidResponseException("Failed to delete source knowledge", null);
        }
    }

    private Map<String, Object> knowledgeItemDocument(KnowledgeItem item) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", item.id());
        document.put("workspaceId", item.workspaceId());
        document.put("sourceType", item.sourceType().apiName());
        document.put("sourceName", item.sourceName());
        document.put("jobId", item.jobId());
        document.put("sourceId", item.sourceId());
        document.put("sourceUrl", item.sourceUrl());
        document.put("content", item.content());
        document.put("extractedAt", item.extractedAt() == null ? null : item.extractedAt().toString());
        document.put("parserVersion", item.parserVersion());
        document.put("chunkId", item.chunkId());
        document.put("chunkSequence", item.chunkSequence());
        document.put("heading", item.heading());
        document.put("pageNumber", item.pageNumber());
        document.put("slideNumber", item.slideNumber());
        document.put("sheetName", item.sheetName());
        document.put("embedding", item.embedding());
        document.put("embeddingModel", item.embeddingModel());
        document.put("embeddingDimensions", item.embeddingDimensions());
        document.put("contentHash", item.contentHash());
        document.put("createdAt", item.createdAt().toString());
        return document;
    }

    @Override
    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        addKnowledgeItem(new KnowledgeItem(
                java.util.UUID.randomUUID().toString(),
                workspaceId,
                sourceTypeFrom(field),
                field.fieldName(),
                null,
                value,
                Instant.now()
        ));
    }

    private void ensureKnowledgeItemsIndex() throws IOException {
        if (knowledgeItemsIndexChecked) {
            return;
        }

        synchronized (this) {
            if (knowledgeItemsIndexChecked) {
                return;
            }

            if (!indexExists(knowledgeItemsIndexUri())) {
                createKnowledgeItemsIndex();
            }
            updateKnowledgeItemsIndexMapping();

            knowledgeItemsIndexChecked = true;
        }
    }

    private void createKnowledgeItemsIndex() throws IOException {
        try {
            restClient.put()
                    .uri(knowledgeItemsIndexUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(knowledgeItemsIndexMapping())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400
                    && exception.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                return;
            }

            throw openSearchResponseException("Failed to create knowledge items index", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to create knowledge items index", exception);
        }
    }

    private String knowledgeItemsIndexMapping() throws IOException {
        return objectMapper.writeValueAsString(Map.of(
                "settings", Map.of("index", Map.of("knn", true)),
                "mappings", Map.of("properties", knowledgeItemProperties())
        ));
    }

    private void updateKnowledgeItemsIndexMapping() throws IOException {
        try {
            restClient.put()
                    .uri(knowledgeItemsMappingUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Map.of("properties", knowledgeItemProperties())))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException("Failed to update workspace knowledge mapping", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to update workspace knowledge mapping", exception);
        }
    }

    private Map<String, Object> knowledgeItemProperties() {
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
        properties.put("heading", Map.of("type", "text"));
        properties.put("pageNumber", Map.of("type", "integer"));
        properties.put("slideNumber", Map.of("type", "integer"));
        properties.put("sheetName", Map.of("type", "keyword"));
        properties.put("embedding", Map.of(
                "type", "knn_vector",
                "dimension", embeddingProperties.dimensions(),
                "method", Map.of(
                        "name", "hnsw",
                        "space_type", "cosinesimil",
                        "engine", "lucene",
                        "parameters", Map.of("ef_construction", 128, "m", 16)
                )
        ));
        properties.put("embeddingModel", Map.of("type", "keyword"));
        properties.put("embeddingDimensions", Map.of("type", "integer"));
        properties.put("contentHash", Map.of("type", "keyword"));
        properties.put("createdAt", Map.of("type", "date"));
        return properties;
    }

    private boolean indexExists(URI uri) throws IOException {
        try {
            restClient.head()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }

            throw openSearchResponseException("Failed to check OpenSearch index", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to check OpenSearch index", exception);
        }
    }

    private List<KnowledgeItem> searchItems(Map<String, Object> request, String errorMessage) throws IOException {
        return searchItems(request, knowledgeItemsSearchUri(), errorMessage);
    }

    private List<KnowledgeItem> searchItems(Map<String, Object> request, URI searchUri, String errorMessage)
            throws IOException {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(searchUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode hits = searchHits(response.getBody(), errorMessage);

            Map<String, KnowledgeItem> items = new LinkedHashMap<>();
            for (JsonNode hit : hits) {
                try {
                    KnowledgeItem item = itemFrom(hit.path("_source"), hit.path("_id").asText());
                    items.putIfAbsent(item.id(), item);
                } catch (RuntimeException exception) {
                    throw openSearchInvalidResponseException(errorMessage, exception);
                }
            }

            return List.copyOf(items.values());
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException(errorMessage, exception);
        } catch (RestClientException exception) {
            throw openSearchClientException(errorMessage, exception);
        }
    }

    private JsonNode searchHits(String responseBody, String errorMessage) {
        JsonNode hits;
        try {
            hits = objectMapper
                    .readTree(responseBody == null ? "{}" : responseBody)
                    .path("hits")
                    .path("hits");
        } catch (IOException exception) {
            throw openSearchInvalidResponseException(errorMessage, exception);
        }

        if (!hits.isArray()) {
            throw openSearchInvalidResponseException(errorMessage, null);
        }

        return hits;
    }

    private KnowledgeItem itemFrom(JsonNode source, String fallbackId) {
        return new KnowledgeItem(
                textValue(source, "id", fallbackId),
                textValue(source, "workspaceId", ""),
                sourceTypeFromApiName(textValue(source, "sourceType", "documents")),
                textValue(source, "sourceName", null),
                textValue(source, "jobId", null),
                textValue(source, "sourceId", null),
                textValue(source, "sourceUrl", null),
                textValue(source, "content", ""),
                instantValue(source, "extractedAt"),
                textValue(source, "parserVersion", null),
                textValue(source, "chunkId", null),
                integerValue(source, "chunkSequence"),
                textValue(source, "heading", null),
                integerValue(source, "pageNumber"),
                integerValue(source, "slideNumber"),
                textValue(source, "sheetName", null),
                floatListValue(source, "embedding"),
                textValue(source, "embeddingModel", null),
                integerValue(source, "embeddingDimensions"),
                textValue(source, "contentHash", null),
                Instant.parse(textValue(source, "createdAt", Instant.EPOCH.toString()))
        );
    }

    private String joinedContent(List<KnowledgeItem> items, KnowledgeSourceType sourceType) {
        return items.stream()
                .filter(item -> item.sourceType() == sourceType)
                .map(KnowledgeItem::content)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(null);
    }

    private KnowledgeSourceType sourceTypeFrom(WorkspaceKnowledgeField field) {
        return switch (field) {
            case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
            case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
            case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
            case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
        };
    }

    private KnowledgeSourceType sourceTypeFromApiName(String apiName) {
        for (KnowledgeSourceType sourceType : KnowledgeSourceType.values()) {
            if (sourceType.apiName().equals(apiName)) {
                return sourceType;
            }
        }

        return KnowledgeSourceType.DOCUMENT;
    }

    private String textValue(JsonNode source, String field, String defaultValue) {
        JsonNode value = source.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }

        return value.asText();
    }

    private Instant instantValue(JsonNode source, String field) {
        String value = textValue(source, field, null);
        return value == null ? null : Instant.parse(value);
    }

    private Integer integerValue(JsonNode source, String field) {
        JsonNode value = source.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private List<Float> floatListValue(JsonNode source, String field) {
        JsonNode values = source.path(field);
        if (!values.isArray()) {
            return null;
        }
        List<Float> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(value.floatValue()));
        return List.copyOf(result);
    }

    private void validateQueryEmbedding(List<Float> queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.size() != embeddingProperties.dimensions()) {
            throw new IllegalArgumentException(
                    "Query embedding must contain " + embeddingProperties.dimensions() + " dimensions"
            );
        }
    }

    private List<KnowledgeItem> reciprocalRankFusion(
            List<KnowledgeItem> lexicalResults,
            List<KnowledgeItem> vectorResults,
            int limit,
            int rankConstant
    ) {
        Map<String, KnowledgeItem> items = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        addRanks(lexicalResults, items, scores, rankConstant);
        addRanks(vectorResults, items, scores, rankConstant);
        return items.values().stream()
                .sorted((left, right) -> Double.compare(scores.get(right.id()), scores.get(left.id())))
                .limit(limit)
                .toList();
    }

    private void addRanks(
            List<KnowledgeItem> rankedItems,
            Map<String, KnowledgeItem> items,
            Map<String, Double> scores,
            int rankConstant
    ) {
        for (int index = 0; index < rankedItems.size(); index++) {
            KnowledgeItem item = rankedItems.get(index);
            items.putIfAbsent(item.id(), item);
            scores.merge(item.id(), 1.0 / (rankConstant + index + 1), Double::sum);
        }
    }

    private URI knowledgeItemsIndexUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX);
    }

    private URI knowledgeItemsSearchUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX + "/_search");
    }

    private URI vectorKnowledgeItemsSearchUri() {
        return knowledgeItemsSearchUri();
    }

    private URI knowledgeItemsBulkUri() {
        return URI.create(baseUri + "/_bulk?refresh=wait_for");
    }

    private URI knowledgeItemsMappingUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX + "/_mapping");
    }

    private URI knowledgeItemsDeleteByQueryUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX
                + "/_delete_by_query?refresh=wait_for&conflicts=proceed");
    }

    private UpstreamServiceException openSearchResponseException(String message, RestClientResponseException exception) {
        return new UpstreamServiceException(
                "OpenSearch",
                message + ": OpenSearch returned HTTP " + exception.getStatusCode().value(),
                exception
        );
    }

    private UpstreamServiceException openSearchClientException(String message, RestClientException exception) {
        return new UpstreamServiceException(
                "OpenSearch",
                message + ": OpenSearch is unavailable",
                exception
        );
    }

    private UpstreamServiceException openSearchInvalidResponseException(String message, Exception exception) {
        if (exception == null) {
            return new UpstreamServiceException("OpenSearch", message + ": OpenSearch returned an invalid response");
        }

        return new UpstreamServiceException(
                "OpenSearch",
                message + ": OpenSearch returned an invalid response",
                exception
        );
    }
}
