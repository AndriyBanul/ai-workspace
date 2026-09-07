package com.aiworkspace.documents.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.DocumentTextBlock;
import java.util.List;

final class DocumentStructuredTextRenderer {

    private DocumentStructuredTextRenderer() {
    }

    static String render(List<DocumentTextBlock> blocks, int maxCharacters) {
        StringBuilder content = new StringBuilder();
        Integer renderedPage = null;
        Integer renderedSlide = null;
        String renderedSheet = null;

        for (DocumentTextBlock block : blocks) {
            if (block.pageNumber() != null && !block.pageNumber().equals(renderedPage)) {
                appendSection(content, "[Page " + block.pageNumber() + "]", maxCharacters);
                renderedPage = block.pageNumber();
            }
            if (block.slideNumber() != null && !block.slideNumber().equals(renderedSlide)) {
                appendSection(content, "[Slide " + block.slideNumber() + "]", maxCharacters);
                renderedSlide = block.slideNumber();
            }
            if (block.sheetName() != null && !block.sheetName().equals(renderedSheet)) {
                appendSection(content, "[Sheet: " + block.sheetName() + "]", maxCharacters);
                renderedSheet = block.sheetName();
            }

            String text = block.type() == DocumentBlockType.HEADING ? "# " + block.text() : block.text();
            appendSection(content, text, maxCharacters);
        }
        return content.toString();
    }

    private static void appendSection(StringBuilder content, String section, int maxCharacters) {
        int separatorLength = content.isEmpty() ? 0 : 2;
        if (content.length() + separatorLength + section.length() > maxCharacters) {
            throw new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED);
        }
        if (separatorLength > 0) {
            content.append("\n\n");
        }
        content.append(section);
    }
}
