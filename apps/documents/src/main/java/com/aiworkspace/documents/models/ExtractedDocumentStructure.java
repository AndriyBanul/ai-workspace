package com.aiworkspace.documents.models;

import java.util.List;

public record ExtractedDocumentStructure(String title, String content, List<DocumentTextBlock> blocks) {

    public ExtractedDocumentStructure {
        blocks = List.copyOf(blocks);
    }
}
