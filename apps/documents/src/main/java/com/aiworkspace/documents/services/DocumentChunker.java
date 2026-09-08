package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    public List<KnowledgeChunk> chunk(List<DocumentTextBlock> blocks, String fallbackContent, int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("Maximum chunk characters must be positive");
        }
        if (blocks == null || blocks.isEmpty()) {
            return chunkText(fallbackContent, maxCharacters);
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        ChunkBuilder current = null;
        String heading = null;

        for (DocumentTextBlock block : blocks) {
            if (block == null || block.text() == null || block.text().isBlank()) {
                continue;
            }

            String blockText = block.text().trim();
            if (block.type() == DocumentBlockType.HEADING) {
                if (current != null && !current.isEmpty()) {
                    chunks.add(current.build(chunks.size() + 1));
                }
                heading = blockText;
                current = new ChunkBuilder(heading, block.pageNumber(), block.slideNumber(), block.sheetName());
                current = append(chunks, current, "# " + blockText, maxCharacters);
                continue;
            }

            if (current == null || !current.sameLocation(block)) {
                if (current != null && !current.isEmpty()) {
                    chunks.add(current.build(chunks.size() + 1));
                }
                current = new ChunkBuilder(heading, block.pageNumber(), block.slideNumber(), block.sheetName());
            }
            current = append(chunks, current, blockText, maxCharacters);
        }

        if (current != null && !current.isEmpty()) {
            chunks.add(current.build(chunks.size() + 1));
        }
        return List.copyOf(chunks);
    }

    public List<KnowledgeChunk> chunkText(String content, int maxCharacters) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<DocumentTextBlock> blocks = new ArrayList<>();
        int sequence = 0;
        for (String paragraph : content.split("\\R\\s*\\R")) {
            if (!paragraph.isBlank()) {
                blocks.add(new DocumentTextBlock(
                        ++sequence,
                        DocumentBlockType.PARAGRAPH,
                        paragraph.trim(),
                        null,
                        null,
                        null
                ));
            }
        }
        return chunk(blocks, content, maxCharacters);
    }

    private ChunkBuilder append(
            List<KnowledgeChunk> chunks,
            ChunkBuilder current,
            String text,
            int maxCharacters
    ) {
        String remaining = text;
        while (!remaining.isEmpty()) {
            int separatorLength = current.isEmpty() ? 0 : 2;
            int available = maxCharacters - current.length() - separatorLength;
            if (available <= 0) {
                chunks.add(current.build(chunks.size() + 1));
                current = current.next();
                continue;
            }

            if (remaining.length() <= available) {
                current.append(remaining);
                break;
            }

            int splitAt = splitAt(remaining, available);
            if (splitAt == 0 && !current.isEmpty()) {
                chunks.add(current.build(chunks.size() + 1));
                current = current.next();
                continue;
            }
            if (splitAt == 0) {
                splitAt = Math.min(remaining.length(), maxCharacters);
            }
            current.append(remaining.substring(0, splitAt).trim());
            chunks.add(current.build(chunks.size() + 1));
            current = current.next();
            remaining = remaining.substring(splitAt).trim();
        }
        return current;
    }

    private int splitAt(String value, int maximum) {
        if (maximum <= 0) {
            return 0;
        }
        int candidate = Math.min(value.length(), maximum);
        if (candidate == value.length()) {
            return candidate;
        }
        for (int index = candidate; index > Math.max(0, candidate / 2); index--) {
            if (Character.isWhitespace(value.charAt(index - 1))) {
                return index;
            }
        }
        return candidate;
    }

    private static final class ChunkBuilder {

        private final String heading;
        private final Integer pageNumber;
        private final Integer slideNumber;
        private final String sheetName;
        private final StringBuilder content = new StringBuilder();

        private ChunkBuilder(String heading, Integer pageNumber, Integer slideNumber, String sheetName) {
            this.heading = heading;
            this.pageNumber = pageNumber;
            this.slideNumber = slideNumber;
            this.sheetName = sheetName;
        }

        private boolean sameLocation(DocumentTextBlock block) {
            return Objects.equals(pageNumber, block.pageNumber())
                    && Objects.equals(slideNumber, block.slideNumber())
                    && Objects.equals(sheetName, block.sheetName());
        }

        private void append(String value) {
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(value);
        }

        private int length() {
            return content.length();
        }

        private boolean isEmpty() {
            return content.isEmpty();
        }

        private ChunkBuilder next() {
            return new ChunkBuilder(heading, pageNumber, slideNumber, sheetName);
        }

        private KnowledgeChunk build(int sequence) {
            return new KnowledgeChunk(sequence, content.toString(), heading, pageNumber, slideNumber, sheetName);
        }
    }
}
