package com.aiworkspace.orchestrator.models;

public record OrchestrationContent(
        String filename,
        String contentType,
        byte[] content
) {

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
