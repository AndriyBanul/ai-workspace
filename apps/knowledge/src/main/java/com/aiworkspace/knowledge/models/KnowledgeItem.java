package com.aiworkspace.knowledge.models;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record KnowledgeItem(
        String id,
        String workspaceId,
        KnowledgeSourceType sourceType,
        String sourceName,
        String jobId,
        String sourceId,
        String sourceUrl,
        String content,
        Instant extractedAt,
        String parserVersion,
        String chunkId,
        Integer chunkSequence,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        List<Float> embedding,
        String embeddingModel,
        Integer embeddingDimensions,
        String contentHash,
        Instant createdAt,
        String sectionId
) {

    public KnowledgeItem(
            String id,
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String sourceId,
            String sourceUrl,
            String content,
            Instant extractedAt,
            String parserVersion,
            String chunkId,
            Integer chunkSequence,
            String heading,
            Integer pageNumber,
            Integer slideNumber,
            String sheetName,
            List<Float> embedding,
            String embeddingModel,
            Integer embeddingDimensions,
            String contentHash,
            Instant createdAt
    ) {
        this(id, workspaceId, sourceType, sourceName, jobId, sourceId, sourceUrl, content, extractedAt,
                parserVersion, chunkId, chunkSequence, heading, pageNumber, slideNumber, sheetName,
                embedding, embeddingModel, embeddingDimensions, contentHash, createdAt, null);
    }

    public KnowledgeItem(
            String id,
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String sourceId,
            String sourceUrl,
            String content,
            Instant extractedAt,
            String parserVersion,
            Instant createdAt
    ) {
        this(
                id,
                workspaceId,
                sourceType,
                sourceName,
                jobId,
                sourceId,
                sourceUrl,
                content,
                extractedAt,
                parserVersion,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt
        );
    }

    public KnowledgeItem(
            String id,
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String content,
            Instant createdAt
    ) {
        this(id, workspaceId, sourceType, sourceName, jobId, null, null, content, null, null, createdAt);
    }
}
