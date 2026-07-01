package com.aiworkspace.orchestrator.models;

import java.util.List;

public record OrchestrationSubmission(
        List<String> submitted,
        List<String> skipped
) {
}
