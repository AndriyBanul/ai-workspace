package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeIndexingService {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeValidator knowledgeValidator;
    private final TextEmbeddingProvider textEmbeddingProvider;
    private final SourceIndexManifestService manifestService;

    public StagedKnowledgeIndex record(String workspaceId, KnowledgeSourceType sourceType, String sourceName, String jobId,
            String value, KnowledgeSourceMetadata sourceMetadata) throws IOException {
        knowledgeValidator.validateKnowledgeItemContent(value);
        knowledgeValidator.validateWorkspaceId(workspaceId);
        String sourceId = sourceMetadata == null ? null : normalizeOptionalValue(sourceMetadata.sourceId());
        String generation = manifestService != null && sourceId != null ? UUID.randomUUID().toString() : null;
        KnowledgeItem item = KnowledgeItem.builder()
                .id(sourceId == null ? UUID.randomUUID().toString()
                        : generation == null ? sourceId + ":0" : sourceId + ":" + generation + ":0")
                .workspaceId(workspaceId.trim())
                .source(buildSource(sourceType, sourceName, jobId, sourceMetadata, generation))
                .content(value.trim())
                .createdAt(Instant.now())
                .build();
        List<KnowledgeItem> items = withEmbeddings(List.of(item));
        return writeItems(workspaceId.trim(), sourceId, generation, items);
    }

    public StagedKnowledgeIndex recordChunks(String workspaceId, KnowledgeSourceType sourceType, String sourceName,
            String jobId,
            List<KnowledgeChunk> chunks, KnowledgeSourceMetadata sourceMetadata, String sourceLabel)
            throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(sourceLabel + " chunks must not be empty");
        }

        String sourceIdentity = sourceMetadata == null ? UUID.randomUUID().toString() : normalizeOptionalValue(sourceMetadata.sourceId());
        if (sourceIdentity == null) {
            sourceIdentity = UUID.randomUUID().toString();
        }
        String sourceId = sourceMetadata == null ? null : normalizeOptionalValue(sourceMetadata.sourceId());
        String generation = manifestService != null && sourceId != null ? UUID.randomUUID().toString() : null;

        Instant createdAt = Instant.now();
        List<KnowledgeItem> items = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException(sourceLabel + " chunk must not be null");
            }
            validateMediaLocation(chunk);
            knowledgeValidator.validateKnowledgeItemContent(chunk.content());
            String chunkId = generation == null ? sourceIdentity + ":" + chunk.sequence()
                    : sourceIdentity + ":" + generation + ":" + chunk.sequence();
            items.add(KnowledgeItem.builder()
                    .id(chunkId)
                    .workspaceId(workspaceId.trim())
                    .source(buildSource(sourceType, sourceName, jobId, sourceMetadata, generation))
                    .content(chunk.content().trim())
                    .chunkMetadata(KnowledgeChunkMetadata.builder()
                            .id(chunkId)
                            .sequence(chunk.sequence())
                            .sectionId(chunk.sectionId())
                            .heading(normalizeOptionalValue(chunk.heading()))
                            .pageNumber(chunk.pageNumber())
                            .slideNumber(chunk.slideNumber())
                            .sheetName(normalizeOptionalValue(chunk.sheetName()))
                            .startMilliseconds(chunk.startMilliseconds())
                            .endMilliseconds(chunk.endMilliseconds())
                            .speaker(normalizeOptionalValue(chunk.speaker()))
                            .build())
                    .createdAt(createdAt)
                    .build());
        }

        List<KnowledgeItem> embeddedItems = withEmbeddings(items);
        return writeItems(workspaceId.trim(), sourceId, generation, embeddedItems);
    }

    private StagedKnowledgeIndex writeItems(String workspaceId, String sourceId, String generation,
            List<KnowledgeItem> items) throws IOException {
        if (sourceId == null) {
            knowledgeRepository.addKnowledgeItems(items);
            return null;
        }
        if (generation == null) {
            knowledgeRepository.replaceKnowledgeItems(workspaceId, sourceId, items);
            return null;
        }
        knowledgeRepository.stageKnowledgeItems(workspaceId, sourceId, generation, items);
        return new StagedKnowledgeIndex(workspaceId, sourceId, generation, items.size());
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

        List<String> inputs = items.stream().map(this::buildEmbeddingInput).toList();
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
                            computeSha256(inputs.get(index))))
                    .createdAt(item.createdAt())
                    .build());
        }
        return List.copyOf(embeddedItems);
    }

    private String buildEmbeddingInput(KnowledgeItem item) {
        StringBuilder input = new StringBuilder();
        if (item.heading() != null && !item.heading().isBlank()) {
            input.append(item.heading()).append("\n\n");
        }
        if (item.speaker() != null && !item.speaker().isBlank()) {
            input.append("Speaker: ").append(item.speaker()).append("\n\n");
        }
        return input.append(item.content()).toString();
    }

    private String computeSha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeOptionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private KnowledgeItemSource buildSource(KnowledgeSourceType sourceType, String sourceName, String jobId,
            KnowledgeSourceMetadata sourceMetadata, String generation) {
        return KnowledgeItemSource.builder()
                .type(sourceType)
                .name(normalizeOptionalValue(sourceName))
                .jobId(normalizeOptionalValue(jobId))
                .id(sourceMetadata == null ? null : normalizeOptionalValue(sourceMetadata.sourceId()))
                .url(sourceMetadata == null ? null : normalizeOptionalValue(sourceMetadata.sourceUrl()))
                .extractedAt(sourceMetadata == null ? null : sourceMetadata.extractedAt())
                .parserVersion(sourceMetadata == null ? null : normalizeOptionalValue(sourceMetadata.parserVersion()))
                .generation(generation)
                .build();
    }
}
