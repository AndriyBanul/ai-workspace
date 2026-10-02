package com.aiworkspace.knowledge.models;

import lombok.Builder;

/** Logical and physical location of an indexed content chunk. */
@Builder
public record KnowledgeChunkMetadata(
        String id,
        Integer sequence,
        String sectionId,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        Long startMilliseconds,
        Long endMilliseconds,
        String speaker
) {
}
