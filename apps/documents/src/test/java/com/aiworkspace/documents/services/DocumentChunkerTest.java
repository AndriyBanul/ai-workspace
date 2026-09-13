package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void groupsFiveSentencesAndPreservesParagraphsAndAbbreviations() {
        var chunks = chunker.chunkText("Dr. Watson arrived. Holmes smiled. Rain fell. They waited. A bell rang.\n\nThe door opened.", 5);
        assertEquals(2, chunks.size());
        assertEquals("Dr. Watson arrived. Holmes smiled. Rain fell. They waited. A bell rang.", chunks.getFirst().content());
        assertEquals("The door opened.", chunks.getLast().content());
        assertEquals(chunks.getFirst().sectionId(), chunks.getLast().sectionId());
    }

    @Test
    void neverCutsAnOversizedSentence() {
        String sentence = "A".repeat(20_000) + ".";
        assertEquals(List.of(sentence), chunker.chunkText(sentence, 5).stream().map(c -> c.content()).toList());
    }

    @Test
    void extractsChaptersFromSinglePlainTextBlockAndDoesNotMergeRepeatedHeadings() {
        var chunks = chunker.chunk(List.of(new DocumentTextBlock(1, DocumentBlockType.PARAGRAPH,
                "I. FIRST STORY\n\nI.\n\nOne sentence.\n\nII. SECOND STORY\n\nAnother sentence.",
                null, null, null)), "", 5);
        var first = chunks.stream().filter(c -> c.content().equals("One sentence.")).findFirst().orElseThrow();
        var last = chunks.getLast();
        assertTrue(first.heading().contains("FIRST STORY"));
        assertTrue(last.heading().contains("SECOND STORY"));
        assertNotEquals(first.sectionId(), last.sectionId());
    }

    @Test
    void preservesParagraphSeparatorsAndPageLocations() {
        var chunks = chunker.chunk(List.of(
                new DocumentTextBlock(1, DocumentBlockType.HEADING, "Revenue", 3, null, null),
                new DocumentTextBlock(2, DocumentBlockType.PARAGRAPH, "First paragraph.", 3, null, null),
                new DocumentTextBlock(3, DocumentBlockType.PARAGRAPH, "Second paragraph.", 3, null, null),
                new DocumentTextBlock(4, DocumentBlockType.PARAGRAPH, "Next page.", 4, null, null)), "", 5);
        assertEquals(2, chunks.size());
        assertEquals("First paragraph.\n\nSecond paragraph.", chunks.getFirst().content());
        assertEquals(3, chunks.getFirst().pageNumber());
        assertEquals(4, chunks.getLast().pageNumber());
        assertEquals(chunks.getFirst().sectionId(), chunks.getLast().sectionId());
    }
}
