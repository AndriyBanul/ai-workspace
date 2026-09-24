package com.aiworkspace.knowledge.models;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Searchable knowledge content composed from provenance, chunk, and embedding metadata.
 *
 * <p>The flattened accessors are retained as a read-side compatibility layer while module callers migrate to the
 * composed model.</p>
 */
@Builder
public record KnowledgeItem(
        String id,
        String workspaceId,
        KnowledgeItemSource source,
        String content,
        KnowledgeChunkMetadata chunkMetadata,
        KnowledgeEmbeddingMetadata embeddingMetadata,
        Instant createdAt
) {

    public KnowledgeSourceType sourceType() {
        return source == null ? null : source.type();
    }

    public String sourceName() {
        return source == null ? null : source.name();
    }

    public String jobId() {
        return source == null ? null : source.jobId();
    }

    public String sourceId() {
        return source == null ? null : source.id();
    }

    public String sourceUrl() {
        return source == null ? null : source.url();
    }

    public Instant extractedAt() {
        return source == null ? null : source.extractedAt();
    }

    public String parserVersion() {
        return source == null ? null : source.parserVersion();
    }

    public String generation() {
        return source == null ? null : source.generation();
    }

    public String chunkId() {
        return chunkMetadata == null ? null : chunkMetadata.id();
    }

    public Integer chunkSequence() {
        return chunkMetadata == null ? null : chunkMetadata.sequence();
    }

    public String sectionId() {
        return chunkMetadata == null ? null : chunkMetadata.sectionId();
    }

    public String heading() {
        return chunkMetadata == null ? null : chunkMetadata.heading();
    }

    public Integer pageNumber() {
        return chunkMetadata == null ? null : chunkMetadata.pageNumber();
    }

    public Integer slideNumber() {
        return chunkMetadata == null ? null : chunkMetadata.slideNumber();
    }

    public String sheetName() {
        return chunkMetadata == null ? null : chunkMetadata.sheetName();
    }

    public Long startMilliseconds() {
        return chunkMetadata == null ? null : chunkMetadata.startMilliseconds();
    }

    public Long endMilliseconds() {
        return chunkMetadata == null ? null : chunkMetadata.endMilliseconds();
    }

    public String speaker() {
        return chunkMetadata == null ? null : chunkMetadata.speaker();
    }

    public List<Float> embedding() {
        return embeddingMetadata == null ? null : embeddingMetadata.vector();
    }

    public String embeddingModel() {
        return embeddingMetadata == null ? null : embeddingMetadata.model();
    }

    public Integer embeddingDimensions() {
        return embeddingMetadata == null ? null : embeddingMetadata.dimensions();
    }

    public String contentHash() {
        return embeddingMetadata == null ? null : embeddingMetadata.contentHash();
    }
}
