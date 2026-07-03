package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record ParsedTextDocument(String filename, String content) {
}
