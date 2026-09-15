package com.aiworkspace.documents.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.DocumentTextBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class DocumentStructureContentHandler extends DefaultHandler {

    private final int maxBlocks;
    private final StringBuilder rawText = new StringBuilder();
    private final List<DocumentTextBlock> blocks = new ArrayList<>();
    private final Deque<Location> locations = new ArrayDeque<>();
    private ActiveBlock activeBlock;
    private Integer pageNumber;
    private Integer slideNumber;
    private String sheetName;
    private String pendingSheetName;
    private boolean spreadsheet;
    private int pageCount;
    private int slideCount;

    DocumentStructureContentHandler(int maxBlocks) {
        this.maxBlocks = maxBlocks;
    }

    void detectedContentType(String contentType) {
        spreadsheet = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType)
                || "application/vnd.ms-excel".equals(contentType);
    }

    @Override
    public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
        locations.push(new Location(pageNumber, slideNumber, sheetName));
        String element = elementName(localName, qualifiedName);
        String className = normalizedAttribute(attributes, "class");

        if (!spreadsheet && hasClass(className, "page")) {
            pageNumber = ++pageCount;
        }
        if (slideNumber == null && (hasClass(className, "slide-content") || hasClass(className, "slide"))) {
            slideNumber = ++slideCount;
        }
        if ("table".equals(element)) {
            String candidateSheetName = firstNonBlank(
                    attribute(attributes, "name"),
                    attribute(attributes, "title")
            );
            if (candidateSheetName != null) {
                sheetName = candidateSheetName;
            } else if (spreadsheet && pendingSheetName != null) {
                sheetName = pendingSheetName;
            }
        }

        if (activeBlock == null) {
            DocumentBlockType blockType = blockType(element);
            if (blockType != null) {
                activeBlock = new ActiveBlock(element, blockType, pageNumber, slideNumber, sheetName);
            }
        } else if (activeBlock.type() == DocumentBlockType.TABLE_ROW
                && ("td".equals(element) || "th".equals(element))) {
            activeBlock.startCell();
        }
    }

    @Override
    public void characters(char[] characters, int start, int length) {
        rawText.append(characters, start, length);
        if (activeBlock != null) {
            activeBlock.append(characters, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
        String element = elementName(localName, qualifiedName);
        if (activeBlock != null && activeBlock.element().equals(element)) {
            addBlock(activeBlock);
            activeBlock = null;
        }

        Location previous = locations.pop();
        pageNumber = previous.pageNumber();
        slideNumber = previous.slideNumber();
        sheetName = previous.sheetName();
    }

    List<DocumentTextBlock> blocks() {
        if (blocks.isEmpty()) {
            String fallback = normalizeText(rawText.toString());
            if (!fallback.isBlank()) {
                return List.of(new DocumentTextBlock(1, DocumentBlockType.PARAGRAPH, fallback, null, null, null));
            }
        }
        return List.copyOf(blocks);
    }

    String structuredText(int maxCharacters) {
        List<DocumentTextBlock> extractedBlocks = blocks();
        if (extractedBlocks.isEmpty()) {
            return normalizeText(rawText.toString());
        }
        return DocumentStructuredTextRenderer.render(extractedBlocks, maxCharacters);
    }

    private void addBlock(ActiveBlock completed) throws SAXException {
        String text = normalizeText(completed.text());
        if (text.isBlank()) {
            return;
        }
        if (blocks.size() >= maxBlocks) {
            throw new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED);
        }
        blocks.add(new DocumentTextBlock(
                blocks.size() + 1,
                completed.type(),
                text,
                completed.pageNumber(),
                completed.slideNumber(),
                completed.sheetName()
        ));
        if (spreadsheet && completed.type() == DocumentBlockType.HEADING
                && completed.pageNumber() == null && completed.slideNumber() == null) {
            pendingSheetName = text;
        }
    }

    private DocumentBlockType blockType(String element) {
        if (element.matches("h[1-6]")) {
            return DocumentBlockType.HEADING;
        }
        return switch (element) {
            case "p" -> DocumentBlockType.PARAGRAPH;
            case "li" -> DocumentBlockType.LIST_ITEM;
            case "tr" -> DocumentBlockType.TABLE_ROW;
            default -> null;
        };
    }

    private String normalizeText(String value) {
        return value.replace('\uFEFF', ' ')
                .replaceAll("\\R", "\n")
                .replaceAll("[\\p{Z}\\t\\x0B\\f]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll(" ?\\| ?", " | ")
                .trim();
    }

    private String elementName(String localName, String qualifiedName) {
        String value = localName == null || localName.isBlank() ? qualifiedName : localName;
        int prefixSeparator = value.indexOf(':');
        if (prefixSeparator >= 0) {
            value = value.substring(prefixSeparator + 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizedAttribute(Attributes attributes, String name) {
        String value = attribute(attributes, name);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String attribute(Attributes attributes, String name) {
        String value = attributes.getValue(name);
        if (value != null) {
            return value;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            if (name.equalsIgnoreCase(elementName(attributes.getLocalName(index), attributes.getQName(index)))) {
                return attributes.getValue(index);
            }
        }
        return null;
    }

    private boolean hasClass(String className, String expected) {
        for (String token : className.split("\\s+")) {
            if (expected.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private record Location(Integer pageNumber, Integer slideNumber, String sheetName) {
    }

    private static final class ActiveBlock {

        private final String element;
        private final DocumentBlockType type;
        private final Integer pageNumber;
        private final Integer slideNumber;
        private final String sheetName;
        private final StringBuilder text = new StringBuilder();
        private int cellCount;

        private ActiveBlock(
                String element,
                DocumentBlockType type,
                Integer pageNumber,
                Integer slideNumber,
                String sheetName
        ) {
            this.element = element;
            this.type = type;
            this.pageNumber = pageNumber;
            this.slideNumber = slideNumber;
            this.sheetName = sheetName;
        }

        void startCell() {
            if (cellCount++ > 0) {
                text.append(" | ");
            }
        }

        void append(char[] characters, int start, int length) {
            text.append(characters, start, length);
        }

        String element() {
            return element;
        }

        DocumentBlockType type() {
            return type;
        }

        Integer pageNumber() {
            return pageNumber;
        }

        Integer slideNumber() {
            return slideNumber;
        }

        String sheetName() {
            return sheetName;
        }

        String text() {
            return text.toString();
        }
    }
}
