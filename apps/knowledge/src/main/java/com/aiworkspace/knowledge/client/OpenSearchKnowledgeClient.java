package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** OpenSearch repository facade that delegates read and write operations to focused adapters. */
@Component
public class OpenSearchKnowledgeClient implements KnowledgeRepository {

    private final OpenSearchKnowledgeSearchReader reader;
    private final OpenSearchKnowledgeIndexWriter writer;

    @Autowired
    public OpenSearchKnowledgeClient(
            @Value("${ai-workspace.opensearch.base-url:http://localhost:9200}") String baseUrl,
            RestClient restClient,
            ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties,
            @Value("${ai-workspace.knowledge.search.heading-weight:2}") int headingWeight,
            SearchTelemetry telemetry
    ) {
        this(normalizedBaseUri(baseUrl), restClient, objectMapper, embeddingProperties, headingWeight, telemetry);
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this(baseUri, restClient, objectMapper,
                new KnowledgeEmbeddingProperties(null, null, null, null, null, null));
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties) {
        this(baseUri, restClient, objectMapper, embeddingProperties, 2);
    }

    OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties, int headingWeight) {
        this(baseUri, restClient, objectMapper, embeddingProperties, headingWeight, SearchTelemetry.NOOP);
    }

    private OpenSearchKnowledgeClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper,
            KnowledgeEmbeddingProperties embeddingProperties, int headingWeight, SearchTelemetry telemetry) {
        OpenSearchKnowledgeStore store = new OpenSearchKnowledgeStore(
                baseUri, restClient, objectMapper, embeddingProperties);
        this.reader = new OpenSearchKnowledgeSearchReader(store, headingWeight, telemetry);
        this.writer = new OpenSearchKnowledgeIndexWriter(store);
    }

    @Override
    public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException {
        return reader.findByWorkspaceId(workspaceId);
    }

    @Override
    public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException {
        return reader.findByWorkspaceIdItems(workspaceId);
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) throws IOException {
        return reader.search(workspaceId, query, limit);
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, List<Float> queryEmbedding,
            int limit, int candidateLimit, int rrfRankConstant) throws IOException {
        return reader.hybridSearch(workspaceId, query, queryEmbedding, limit, candidateLimit, rrfRankConstant);
    }

    @Override
    public List<KnowledgeItem> searchKnowledgeItemsByVector(String workspaceId, List<Float> queryEmbedding,
            int limit, int candidateLimit) throws IOException {
        return reader.vectorSearch(workspaceId, queryEmbedding, limit, candidateLimit);
    }

    @Override
    public List<KnowledgeItem> expandNeighbors(String workspaceId, List<KnowledgeItem> matches) throws IOException {
        return reader.expandNeighbors(workspaceId, matches);
    }

    @Override
    public void addKnowledgeItem(KnowledgeItem item) throws IOException {
        writer.add(List.of(item));
    }

    @Override
    public void addKnowledgeItems(List<KnowledgeItem> items) throws IOException {
        writer.add(items);
    }

    @Override
    public void replaceKnowledgeItems(String workspaceId, String sourceId, List<KnowledgeItem> items)
            throws IOException {
        writer.replace(workspaceId, sourceId, items);
    }

    @Override
    public void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) throws IOException {
        writer.deleteSource(workspaceId, sourceId);
    }

    @Override
    public void deleteKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException {
        writer.deleteWorkspace(workspaceId);
    }

    @Override
    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        writer.add(List.of(KnowledgeItem.builder()
                .id(UUID.randomUUID().toString())
                .workspaceId(workspaceId)
                .source(KnowledgeItemSource.builder()
                        .type(sourceTypeFrom(field))
                        .name(field.fieldName())
                        .build())
                .content(value)
                .createdAt(Instant.now())
                .build()));
    }

    private KnowledgeSourceType sourceTypeFrom(WorkspaceKnowledgeField field) {
        return switch (field) {
            case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
            case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
            case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
            case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
        };
    }

    private static URI normalizedBaseUri(String baseUrl) {
        return URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
    }
}
