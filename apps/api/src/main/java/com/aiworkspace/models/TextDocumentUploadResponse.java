package com.aiworkspace.models;

public record TextDocumentUploadResponse(String filename, long sizeBytes, int characterCount) {
}
