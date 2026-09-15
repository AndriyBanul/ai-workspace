package com.aiworkspace.knowledge.models;

import lombok.Builder;

@Builder
public record KnowledgeChunk(
        int sequence,
        String content,
        String heading,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        String sectionId,
        Long startMilliseconds,
        Long endMilliseconds,
        String speaker
) {
    public KnowledgeChunk(int sequence, String content, String heading, Integer pageNumber,
            Integer slideNumber, String sheetName) {
        this(sequence, content, heading, pageNumber, slideNumber, sheetName, null, null, null, null);
    }

    public KnowledgeChunk(int sequence, String content, String heading, Integer pageNumber,
            Integer slideNumber, String sheetName, String sectionId) {
        this(sequence, content, heading, pageNumber, slideNumber, sheetName, sectionId, null, null, null);
    }
}
