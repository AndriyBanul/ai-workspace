package com.aiworkspace.documents.services;

import com.aiworkspace.documents.config.DocumentExtractionProperties;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.DocumentTextBlock;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.Version;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

@Component
class DocxStructureExtractor {

    private static final String PARSER_VERSION = "ai-workspace-docx-structure-v1/poi-" + Version.getVersion();

    String parserVersion() {
        return PARSER_VERSION;
    }

    ExtractedDocumentStructure extract(byte[] bytes, DocumentExtractionProperties properties) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<DocumentTextBlock> blocks = new ArrayList<>();
            int extractedCharacters = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    extractedCharacters = addBlock(
                            blocks,
                            paragraphType(paragraph),
                            paragraph.getText(),
                            extractedCharacters,
                            properties
                    );
                } else if (element instanceof XWPFTable table) {
                    for (var row : table.getRows()) {
                        extractedCharacters = addBlock(
                                blocks,
                                DocumentBlockType.TABLE_ROW,
                                row.getTableCells().stream()
                                        .map(cell -> normalizeText(cell.getText()))
                                        .collect(Collectors.joining(" | ")),
                                extractedCharacters,
                                properties
                        );
                    }
                }
            }

            String content = DocumentStructuredTextRenderer.render(blocks, properties.maxExtractedCharacters());
            return new ExtractedDocumentStructure(
                    normalizeOptional(document.getProperties().getCoreProperties().getTitle()),
                    content,
                    blocks
            );
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Failed to extract DOCX structure", exception);
        }
    }

    private int addBlock(
            List<DocumentTextBlock> blocks,
            DocumentBlockType type,
            String rawText,
            int extractedCharacters,
            DocumentExtractionProperties properties
    ) {
        String text = normalizeText(rawText);
        if (text.isBlank()) {
            return extractedCharacters;
        }
        if (blocks.size() >= properties.maxExtractedBlocks()
                || extractedCharacters + text.length() > properties.maxExtractedCharacters()) {
            throw new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED);
        }
        blocks.add(new DocumentTextBlock(blocks.size() + 1, type, text, null, null, null));
        return extractedCharacters + text.length();
    }

    private DocumentBlockType paragraphType(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null) {
            String normalizedStyle = style.toLowerCase(Locale.ROOT);
            if (normalizedStyle.startsWith("heading") || normalizedStyle.startsWith("title")) {
                return DocumentBlockType.HEADING;
            }
        }
        if (paragraph.getNumID() != null) {
            return DocumentBlockType.LIST_ITEM;
        }
        return DocumentBlockType.PARAGRAPH;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\uFEFF', ' ')
                .replaceAll("[\\p{Z}\\s]+", " ")
                .trim();
    }

    private String normalizeOptional(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? null : normalized;
    }
}
