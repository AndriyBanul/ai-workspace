package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchKnowledgeDocument(
        String id,
        String workspaceId,
        String sourceType,
        String sourceName,
        String jobId,
        String sourceId,
        String generation,
        String sourceUrl,
        String content,
        String extractedAt,
        String parserVersion,
        String chunkId,
        Integer chunkSequence,
        String sectionId,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        Long startMilliseconds,
        Long endMilliseconds,
        String speaker,
        List<Float> embedding,
        String embeddingModel,
        Integer embeddingDimensions,
        String contentHash,
        String createdAt
) {
}
