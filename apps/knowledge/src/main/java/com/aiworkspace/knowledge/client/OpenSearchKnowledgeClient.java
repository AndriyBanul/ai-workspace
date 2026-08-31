package com.aiworkspace.knowledge.client;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private volatile boolean knowledgeItemsIndexChecked;

    @Autowired
    public OpenSearchKnowledgeClient(
            @Value("${ai-workspace.opensearch.base-url:http://localhost:9200}") String baseUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                restClient,
                objectMapper
        );
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
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
        request.put("query", Map.of("term", Map.of("workspaceId", workspaceId)));
        request.put("sort", List.of(Map.of("createdAt", Map.of("order", "asc"))));

        return searchItems(request, "Failed to fetch workspace knowledge items");
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) throws IOException {
        ensureKnowledgeItemsIndex();

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", List.of(Map.of("term", Map.of("workspaceId", workspaceId))));
        bool.put("must", List.of(Map.of("match", Map.of("content", Map.of("query", query)))));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", limit);
        request.put("query", Map.of("bool", bool));

        return searchItems(request, "Failed to search workspace knowledge items");
    }

    @Override
    public void addKnowledgeItem(KnowledgeItem item) throws IOException {
        ensureKnowledgeItemsIndex();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", item.id());
        document.put("workspaceId", item.workspaceId());
        document.put("sourceType", item.sourceType().apiName());
        document.put("sourceName", item.sourceName());
        document.put("jobId", item.jobId());
        document.put("content", item.content());
        document.put("createdAt", item.createdAt().toString());

        try {
            restClient.put()
                    .uri(knowledgeItemDocumentUri(item.id()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(document))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException("Failed to add workspace knowledge item", exception);
        } catch (RestClientException exception) {
            throw openSearchClientException("Failed to add workspace knowledge item", exception);
        }
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
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("workspaceId", Map.of("type", "keyword"));
        properties.put("sourceType", Map.of("type", "keyword"));
        properties.put("sourceName", Map.of("type", "keyword"));
        properties.put("jobId", Map.of("type", "keyword"));
        properties.put("content", Map.of("type", "text"));
        properties.put("createdAt", Map.of("type", "date"));

        return objectMapper.writeValueAsString(Map.of("mappings", Map.of("properties", properties)));
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
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(knowledgeItemsSearchUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode hits = objectMapper
                    .readTree(response.getBody() == null ? "{}" : response.getBody())
                    .path("hits")
                    .path("hits");

            List<KnowledgeItem> items = new ArrayList<>();
            for (JsonNode hit : hits) {
                items.add(itemFrom(hit.path("_source"), hit.path("_id").asText()));
            }

            return items;
        } catch (RestClientResponseException exception) {
            throw openSearchResponseException(errorMessage, exception);
        } catch (RestClientException exception) {
            throw openSearchClientException(errorMessage, exception);
        }
    }

    private KnowledgeItem itemFrom(JsonNode source, String fallbackId) {
        return new KnowledgeItem(
                textValue(source, "id", fallbackId),
                textValue(source, "workspaceId", ""),
                sourceTypeFromApiName(textValue(source, "sourceType", "documents")),
                textValue(source, "sourceName", null),
                textValue(source, "jobId", null),
                textValue(source, "content", ""),
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

    private URI knowledgeItemsIndexUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX);
    }

    private URI knowledgeItemsSearchUri() {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX + "/_search");
    }

    private URI knowledgeItemDocumentUri(String id) {
        return URI.create(baseUri + "/" + KNOWLEDGE_ITEMS_INDEX + "/_doc/" + encodePathSegment(id) + "?refresh=wait_for");
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
}
