package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void preservesHeadingAndPageMetadataAcrossBoundedChunks() {
        List<DocumentTextBlock> blocks = List.of(
                new DocumentTextBlock(1, DocumentBlockType.HEADING, "Revenue", 3, null, null),
                new DocumentTextBlock(2, DocumentBlockType.PARAGRAPH, "First paragraph", 3, null, null),
                new DocumentTextBlock(3, DocumentBlockType.PARAGRAPH, "Second paragraph is longer", 3, null, null),
                new DocumentTextBlock(4, DocumentBlockType.PARAGRAPH, "Next page", 4, null, null)
        );

        var chunks = chunker.chunk(blocks, "", 32);

        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 32));
        assertEquals("Revenue", chunks.get(0).heading());
        assertEquals(3, chunks.get(0).pageNumber());
        assertEquals(4, chunks.getLast().pageNumber());
        for (int index = 0; index < chunks.size(); index++) {
            assertEquals(index + 1, chunks.get(index).sequence());
        }
    }

    @Test
    void splitsLongUnstructuredTextWithoutLosingContent() {
        String content = "A".repeat(85);

        var chunks = chunker.chunkText(content, 32);

        assertEquals(3, chunks.size());
        assertEquals(content, chunks.stream().map(chunk -> chunk.content()).reduce("", String::concat));
    }
}
