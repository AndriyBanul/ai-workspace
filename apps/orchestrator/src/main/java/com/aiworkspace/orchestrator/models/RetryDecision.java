package com.aiworkspace.orchestrator.models;

import java.time.Instant;

public record RetryDecision(boolean retryable, Instant nextAttemptAt) {
}
