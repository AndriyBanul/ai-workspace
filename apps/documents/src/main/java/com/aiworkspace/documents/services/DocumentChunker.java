package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    public List<KnowledgeChunk> chunk(List<DocumentTextBlock> blocks, String fallbackContent, int maxSentences) {
        if (maxSentences <= 0) {
            throw new IllegalArgumentException("Maximum chunk sentences must be positive");
        }
        if (blocks == null || blocks.isEmpty()) {
            return chunkText(fallbackContent, maxSentences);
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        Builder current = null;
        String heading = null;
        var headingPath = new ArrayList<String>();
        int section = 0;
        var paragraphs = new ArrayList<DocumentTextBlock>();
        for (var original : blocks) {
            if (original == null || original.text() == null) continue;
            for (String part : original.text().split("\\R\\s*\\R")) {
                paragraphs.add(new DocumentTextBlock(original.sequence(), original.type(), part,
                        original.pageNumber(), original.slideNumber(), original.sheetName()));
            }
        }
        for (var block : paragraphs) {
            if (block == null || block.text() == null || block.text().isBlank()) continue;
            String text = block.text().trim();
            if (block.type() == DocumentBlockType.HEADING || isPlainTextHeading(text)) {
                if (current != null) current.flush(chunks);
                int level = headingLevel(text);
                while (headingPath.size() >= level) headingPath.removeLast();
                headingPath.add(text);
                heading = String.join(" > ", headingPath);
                section++;
                current = new Builder(heading, Integer.toString(section), block);
                continue;
            }
            if (current == null || !current.sameLocation(block)) {
                if (current != null) current.flush(chunks);
                current = new Builder(heading, Integer.toString(section), block);
            }
            boolean firstInParagraph = true;
            for (String sentence : sentences(text)) {
                if (current.sentences == maxSentences) current.flush(chunks);
                current.append(sentence, firstInParagraph);
                firstInParagraph = false;
            }
        }
        if (current != null) current.flush(chunks);
        return List.copyOf(chunks);
    }

    public List<KnowledgeChunk> chunkText(String content, int maxSentences) {
        if (maxSentences <= 0) throw new IllegalArgumentException("Maximum chunk sentences must be positive");
        if (content == null || content.isBlank()) return List.of();
        var blocks = new ArrayList<DocumentTextBlock>();
        for (String paragraph : content.split("\\R\\s*\\R")) {
            if (!paragraph.isBlank()) blocks.add(new DocumentTextBlock(blocks.size() + 1,
                    DocumentBlockType.PARAGRAPH, paragraph.trim(), null, null, null));
        }
        return chunk(blocks, content, maxSentences);
    }

    private List<String> sentences(String text) {
        var iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(text);
        var result = new ArrayList<String>();
        var pending = new StringBuilder();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            pending.append(text, start, end);
            if (pending.toString().trim().matches("(?s).*(?:\\b(?:Mr|Mrs|Ms|Dr|Prof|St)|\\b[A-Z])\\.$")) continue;
            if (!pending.toString().isBlank()) result.add(pending.toString().trim());
            pending.setLength(0);
        }
        if (!pending.toString().isBlank()) result.add(pending.toString().trim());
        return result;
    }

    private int headingLevel(String text) {
        if (text.startsWith("#")) {
            int level = 0;
            while (level < text.length() && text.charAt(level) == '#') level++;
            return Math.min(6, level);
        }
        if (text.matches("^[IVXLCDM]+\\.$") || text.matches("(?i)^section\\s+.*")) return 2;
        return 1;
    }

    private boolean isPlainTextHeading(String text) {
        // Conservative markers only; arbitrary short paragraphs are not headings.
        return text.matches("^[IVXLCDM]+\\.$")
                || text.matches("(?s)^#{1,6}\\s+[^\\r\\n]+$")
                || text.matches("(?iu)^(chapter|section|part)\\s+[\\p{L}\\d]+(?:[.: —-].*)?$")
                || text.matches("^[IVXLCDM]+\\. [A-Z][A-Z ’'—-]+$");
    }

    private static final class Builder {
        private final String heading;
        private final String sectionId;
        private final DocumentTextBlock location;
        private final StringBuilder content = new StringBuilder();
        private int sentences;
        private boolean emitted;

        private Builder(String heading, String sectionId, DocumentTextBlock location) {
            this.heading = heading;
            this.sectionId = sectionId;
            this.location = location;
        }
        private boolean sameLocation(DocumentTextBlock block) {
            return Objects.equals(location.pageNumber(), block.pageNumber())
                    && Objects.equals(location.slideNumber(), block.slideNumber())
                    && Objects.equals(location.sheetName(), block.sheetName());
        }
        private void append(String sentence, boolean paragraphStart) {
            if (!content.isEmpty()) content.append(paragraphStart ? "\n\n" : " ");
            content.append(sentence);
            sentences++;
        }
        private void flush(List<KnowledgeChunk> chunks) {
            if (content.isEmpty()) {
                if (emitted || heading == null) return;
                content.append(heading);
            }
            emitted = true;
            chunks.add(new KnowledgeChunk(chunks.size() + 1, content.toString(), heading,
                    location.pageNumber(), location.slideNumber(), location.sheetName(), sectionId));
            content.setLength(0);
            sentences = 0;
        }
    }
}
