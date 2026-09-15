package com.aiworkspace.knowledge.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record WorkspaceKnowledgeSource(
        String id,
        String type,
        String sourceName,
        String jobId,
        String sourceId,
        String sourceUrl,
        Instant extractedAt,
        String parserVersion,
        String chunkId,
        Integer chunkSequence,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        Long startMilliseconds,
        Long endMilliseconds,
        String speaker,
        String snippet,
        String sourceFileKey
) {
}
