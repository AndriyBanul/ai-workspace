package com.aiworkspace.documents.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentExtractionPropertiesTest {

    @Test
    void usesProductionDefaultsWhenPropertiesAreMissing() {
        DocumentExtractionProperties properties = new DocumentExtractionProperties(null, null, null, null, null);

        assertEquals(1_000_000, properties.maxExtractedCharacters());
        assertEquals(10_000, properties.maxExtractedBlocks());
        assertEquals(64L * 1024 * 1024, properties.maxPdfMainMemoryBytes());
        assertFalse(properties.extractEmbeddedDocuments());
        assertEquals(2_000, properties.maxChunkCharacters());
    }

    @Test
    void preservesConfiguredValues() {
        DocumentExtractionProperties properties = new DocumentExtractionProperties(25_000, 250, 8_000_000L, true, 750);

        assertEquals(25_000, properties.maxExtractedCharacters());
        assertEquals(250, properties.maxExtractedBlocks());
        assertEquals(8_000_000L, properties.maxPdfMainMemoryBytes());
        assertTrue(properties.extractEmbeddedDocuments());
        assertEquals(750, properties.maxChunkCharacters());
    }

    @Test
    void rejectsNonPositiveCharacterLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentExtractionProperties(0, null, null, null, null)
        );

        assertEquals("Document extraction max characters must be positive", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveBlockLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentExtractionProperties(null, 0, null, null, null)
        );

        assertEquals("Document extraction max blocks must be positive", exception.getMessage());
    }

    @Test
    void rejectsNonPositivePdfMemoryLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentExtractionProperties(null, null, 0L, null, null)
        );

        assertEquals("Document extraction PDF main-memory limit must be positive", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveChunkLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentExtractionProperties(null, null, null, null, 0)
        );

        assertEquals("Document extraction max chunk characters must be positive", exception.getMessage());
    }
}
