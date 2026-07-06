package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record TextDocumentUploadResponse(String filename, long sizeBytes, int characterCount) {
}
