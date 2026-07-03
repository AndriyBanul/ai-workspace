package com.aiworkspace.models;

import lombok.Builder;

@Builder
public record TextDocumentUploadResponse(String filename, long sizeBytes, int characterCount) {
}
