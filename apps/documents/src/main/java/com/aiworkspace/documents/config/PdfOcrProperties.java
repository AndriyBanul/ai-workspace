package com.aiworkspace.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai-workspace.documents.ocr")
public record PdfOcrProperties(Boolean enabled, Integer maxPages, Integer dpi, Long maxPixels) {
    public PdfOcrProperties {
        if (enabled == null) enabled = true;
        if (maxPages == null) maxPages = 50;
        if (dpi == null) dpi = 150;
        if (maxPixels == null) maxPixels = 4_000_000L;
        if (maxPages < 1 || dpi < 1 || maxPixels < 1) {
            throw new IllegalArgumentException("PDF OCR limits must be positive");
        }
    }
}
