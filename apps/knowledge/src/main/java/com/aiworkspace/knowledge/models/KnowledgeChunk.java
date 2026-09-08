package com.aiworkspace.knowledge.models;

import lombok.Builder;

@Builder
public record KnowledgeChunk(
        int sequence,
        String content,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName
) {
}
