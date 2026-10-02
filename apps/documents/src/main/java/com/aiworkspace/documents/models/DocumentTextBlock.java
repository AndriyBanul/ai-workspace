package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record DocumentTextBlock(
        int sequence,
        DocumentBlockType type,
        String text,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName
) {
}
