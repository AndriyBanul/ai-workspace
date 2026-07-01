package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenSearchKnowledgeClient implements KnowledgeRepository {

    private static final String WORKSPACE_INDEX = "workspace";

    private final URI baseUri;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private volatile boolean workspaceIndexChecked;

    @Autowired
    public OpenSearchKnowledgeClient(
            @Value("${ai-workspace.opensearch.base-url:http://localhost:9200}") String baseUrl,
            RestClient restClient
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                restClient,
                new ObjectMapper()
        );
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException {
        ensureWorkspaceIndex();

        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(documentUri(workspaceId))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode source = objectMapper
                    .readTree(response.getBody() == null ? "{}" : response.getBody())
                    .path("_source");

            return Optional.of(WorkspaceKnowledge.fromSource(workspaceId, source));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }

            throw openSearchException("Failed to fetch workspace knowledge", exception);
        }
    }

    @Override
    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        ensureWorkspaceIndex();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("workspaceId", workspaceId);
        document.put(field.fieldName(), value);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("doc", document);
        request.put("doc_as_upsert", true);

        try {
            restClient.post()
                    .uri(updateDocumentUri(workspaceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw openSearchException("Failed to update workspace knowledge", exception);
        }
    }

    private void ensureWorkspaceIndex() throws IOException {
        if (workspaceIndexChecked) {
            return;
        }

        synchronized (this) {
            if (workspaceIndexChecked) {
                return;
            }

            if (!workspaceIndexExists()) {
                createWorkspaceIndex();
            }

            workspaceIndexChecked = true;
        }
    }

    private boolean workspaceIndexExists() throws IOException {
        try {
            restClient.head()
                    .uri(indexUri())
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }

            throw openSearchException("Failed to check workspace index", exception);
        }
    }

    private void createWorkspaceIndex() throws IOException {
        try {
            restClient.put()
                    .uri(indexUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(workspaceIndexMapping())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400
                    && exception.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                return;
            }

            throw openSearchException("Failed to create workspace index", exception);
        }
    }

    private String workspaceIndexMapping() throws IOException {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", Map.of("type", "keyword"));
        properties.put("documentsInfo", Map.of("type", "text"));
        properties.put("audioInfo", Map.of("type", "text"));
        properties.put("videoInfo", Map.of("type", "text"));
        properties.put("imagesInfo", Map.of("type", "text"));

        return objectMapper.writeValueAsString(Map.of("mappings", Map.of("properties", properties)));
    }

    private URI indexUri() {
        return URI.create(baseUri + "/" + WORKSPACE_INDEX);
    }

    private URI documentUri(String workspaceId) {
        return URI.create(baseUri + "/" + WORKSPACE_INDEX + "/_doc/" + encodePathSegment(workspaceId));
    }

    private URI updateDocumentUri(String workspaceId) {
        return URI.create(baseUri + "/" + WORKSPACE_INDEX + "/_update/" + encodePathSegment(workspaceId));
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private IOException openSearchException(String message, RestClientResponseException exception) {
        return new IOException(
                message + ": OpenSearch returned HTTP " + exception.getStatusCode().value() + ": "
                        + exception.getResponseBodyAsString(),
                exception
        );
    }
}
