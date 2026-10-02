package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.DocumentTextBlock;
import java.util.List;

record ExtractedDocumentStructure(String title, String content, List<DocumentTextBlock> blocks) {

    ExtractedDocumentStructure {
        blocks = List.copyOf(blocks);
    }
}
