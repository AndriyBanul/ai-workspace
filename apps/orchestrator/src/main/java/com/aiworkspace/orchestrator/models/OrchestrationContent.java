package com.aiworkspace.orchestrator.models;

import lombok.Builder;

@Builder
public record OrchestrationContent(
        String filename,
        String contentType,
        byte[] content
) {

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
