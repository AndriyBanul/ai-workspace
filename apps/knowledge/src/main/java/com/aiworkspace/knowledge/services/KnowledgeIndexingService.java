package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexingService {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeValidator knowledgeValidator;
    private final TextEmbeddingProvider textEmbeddingProvider;

    public KnowledgeIndexingService(KnowledgeRepository knowledgeRepository, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider) {
        this.knowledgeRepository = knowledgeRepository;
        this.knowledgeValidator = knowledgeValidator;
        this.textEmbeddingProvider = textEmbeddingProvider;
    }

    public void record(String workspaceId, KnowledgeSourceType sourceType, String sourceName, String jobId,
            String value, KnowledgeSourceMetadata sourceMetadata) throws IOException {
        knowledgeValidator.validateKnowledgeItemContent(value);
        knowledgeValidator.validateWorkspaceId(workspaceId);
        String sourceId = sourceMetadata == null ? null : normalized(sourceMetadata.sourceId());
        KnowledgeItem item = KnowledgeItem.builder()
                .id(sourceId == null ? UUID.randomUUID().toString() : sourceId + ":0")
                .workspaceId(workspaceId.trim())
                .source(source(sourceType, sourceName, jobId, sourceMetadata))
                .content(value.trim())
                .createdAt(Instant.now())
                .build();
        List<KnowledgeItem> items = withEmbeddings(List.of(item));
        if (sourceId == null) {
            knowledgeRepository.addKnowledgeItems(items);
        } else {
            knowledgeRepository.replaceKnowledgeItems(workspaceId.trim(), sourceId, items);
        }
    }

    public void recordChunks(String workspaceId, KnowledgeSourceType sourceType, String sourceName, String jobId,
            List<KnowledgeChunk> chunks, KnowledgeSourceMetadata sourceMetadata, String sourceLabel)
            throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(sourceLabel + " chunks must not be empty");
        }

        String sourceIdentity = sourceMetadata == null ? UUID.randomUUID().toString() : normalized(sourceMetadata.sourceId());
        if (sourceIdentity == null) {
            sourceIdentity = UUID.randomUUID().toString();
        }

        Instant createdAt = Instant.now();
        List<KnowledgeItem> items = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException(sourceLabel + " chunk must not be null");
            }
            validateMediaLocation(chunk);
            knowledgeValidator.validateKnowledgeItemContent(chunk.content());
            String chunkId = sourceIdentity + ":" + chunk.sequence();
            items.add(KnowledgeItem.builder()
                    .id(chunkId)
                    .workspaceId(workspaceId.trim())
                    .source(source(sourceType, sourceName, jobId, sourceMetadata))
                    .content(chunk.content().trim())
                    .chunkMetadata(KnowledgeChunkMetadata.builder()
                            .id(chunkId)
                            .sequence(chunk.sequence())
                            .sectionId(chunk.sectionId())
                            .heading(normalized(chunk.heading()))
                            .pageNumber(chunk.pageNumber())
                            .slideNumber(chunk.slideNumber())
                            .sheetName(normalized(chunk.sheetName()))
                            .startMilliseconds(chunk.startMilliseconds())
                            .endMilliseconds(chunk.endMilliseconds())
                            .speaker(normalized(chunk.speaker()))
                            .build())
                    .createdAt(createdAt)
                    .build());
        }

        List<KnowledgeItem> embeddedItems = withEmbeddings(items);
        String sourceId = sourceMetadata == null ? null : normalized(sourceMetadata.sourceId());
        if (sourceId == null) {
            knowledgeRepository.addKnowledgeItems(embeddedItems);
        } else {
            knowledgeRepository.replaceKnowledgeItems(workspaceId.trim(), sourceId, embeddedItems);
        }
    }

    public void deleteSource(String workspaceId, String sourceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeValidator.validateSourceId(sourceId);
        knowledgeRepository.deleteKnowledgeItemsBySourceId(workspaceId.trim(), sourceId.trim());
    }

    public void deleteWorkspace(String workspaceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeRepository.deleteKnowledgeItemsByWorkspaceId(workspaceId.trim());
    }

    private void validateMediaLocation(KnowledgeChunk chunk) {
        if (chunk.startMilliseconds() != null && chunk.startMilliseconds() < 0) {
            throw new IllegalArgumentException("Chunk start time must not be negative");
        }
        if (chunk.endMilliseconds() != null && chunk.startMilliseconds() == null) {
            throw new IllegalArgumentException("Chunk end time requires a start time");
        }
        if (chunk.endMilliseconds() != null && chunk.endMilliseconds() < chunk.startMilliseconds()) {
            throw new IllegalArgumentException("Chunk end time must not be before its start time");
        }
    }

    private List<KnowledgeItem> withEmbeddings(List<KnowledgeItem> items) throws IOException {
        if (textEmbeddingProvider == null) {
            return items;
        }
        if (!textEmbeddingProvider.isConfigured()) {
            throw new IllegalStateException("Text embedding provider is not configured");
        }

        List<String> inputs = items.stream().map(this::embeddingInput).toList();
        List<List<Float>> embeddings = textEmbeddingProvider.embedDocuments(inputs);
        if (embeddings.size() != items.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected number of vectors");
        }

        List<KnowledgeItem> embeddedItems = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            List<Float> embedding = embeddings.get(index);
            if (embedding.size() != textEmbeddingProvider.dimensions()) {
                throw new IllegalStateException("Embedding provider returned a vector with unexpected dimensions");
            }
            embeddedItems.add(KnowledgeItem.builder()
                    .id(item.id())
                    .workspaceId(item.workspaceId())
                    .source(item.source())
                    .content(item.content())
                    .chunkMetadata(item.chunkMetadata())
                    .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                            embedding,
                            textEmbeddingProvider.model(),
                            textEmbeddingProvider.dimensions(),
                            sha256(inputs.get(index))))
                    .createdAt(item.createdAt())
                    .build());
        }
        return List.copyOf(embeddedItems);
    }

    private String embeddingInput(KnowledgeItem item) {
        StringBuilder input = new StringBuilder();
        if (item.heading() != null && !item.heading().isBlank()) {
            input.append(item.heading()).append("\n\n");
        }
        if (item.speaker() != null && !item.speaker().isBlank()) {
            input.append("Speaker: ").append(item.speaker()).append("\n\n");
        }
        return input.append(item.content()).toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private KnowledgeItemSource source(KnowledgeSourceType sourceType, String sourceName, String jobId,
            KnowledgeSourceMetadata sourceMetadata) {
        return KnowledgeItemSource.builder()
                .type(sourceType)
                .name(normalized(sourceName))
                .jobId(normalized(jobId))
                .id(sourceMetadata == null ? null : normalized(sourceMetadata.sourceId()))
                .url(sourceMetadata == null ? null : normalized(sourceMetadata.sourceUrl()))
                .extractedAt(sourceMetadata == null ? null : sourceMetadata.extractedAt())
                .parserVersion(sourceMetadata == null ? null : normalized(sourceMetadata.parserVersion()))
                .build();
    }
}
