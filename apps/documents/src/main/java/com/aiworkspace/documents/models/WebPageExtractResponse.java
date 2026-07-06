package com.aiworkspace.documents.models;

public record WebPageExtractResponse(
        String url,
        String title,
        int characterCount,
        int loggedCharacterCount,
        boolean truncated
) {
}
