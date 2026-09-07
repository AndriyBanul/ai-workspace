package com.aiworkspace.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-workspace.documents.extraction")
public record DocumentExtractionProperties(
        Integer maxExtractedCharacters,
        Integer maxExtractedBlocks,
        Long maxPdfMainMemoryBytes,
        Boolean extractEmbeddedDocuments
) {

    private static final int DEFAULT_MAX_EXTRACTED_CHARACTERS = 1_000_000;
    private static final int DEFAULT_MAX_EXTRACTED_BLOCKS = 10_000;
    private static final long DEFAULT_MAX_PDF_MAIN_MEMORY_BYTES = 64L * 1024 * 1024;

    public DocumentExtractionProperties {
        if (maxExtractedCharacters == null) {
            maxExtractedCharacters = DEFAULT_MAX_EXTRACTED_CHARACTERS;
        }
        if (maxExtractedCharacters <= 0) {
            throw new IllegalArgumentException("Document extraction max characters must be positive");
        }

        if (maxExtractedBlocks == null) {
            maxExtractedBlocks = DEFAULT_MAX_EXTRACTED_BLOCKS;
        }
        if (maxExtractedBlocks <= 0) {
            throw new IllegalArgumentException("Document extraction max blocks must be positive");
        }

        if (maxPdfMainMemoryBytes == null) {
            maxPdfMainMemoryBytes = DEFAULT_MAX_PDF_MAIN_MEMORY_BYTES;
        }
        if (maxPdfMainMemoryBytes <= 0) {
            throw new IllegalArgumentException("Document extraction PDF main-memory limit must be positive");
        }

        if (extractEmbeddedDocuments == null) {
            extractEmbeddedDocuments = false;
        }
    }
}
