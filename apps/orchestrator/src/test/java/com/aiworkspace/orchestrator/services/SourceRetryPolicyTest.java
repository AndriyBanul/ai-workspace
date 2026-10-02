package com.aiworkspace.orchestrator.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    private final SourceRetryPolicy policy = new SourceRetryPolicy(properties());

    @Test
    void retriesTransientFailuresWithExponentialBackoff() {
        SourceRetryPolicy.RetryDecision first = policy.decide(new IOException("temporarily unavailable"), 1, NOW);
        SourceRetryPolicy.RetryDecision third = policy.decide(new IOException("temporarily unavailable"), 3, NOW);

        assertTrue(first.retryable());
        assertEquals(NOW.plusSeconds(30), first.nextAttemptAt());
        assertTrue(third.retryable());
        assertEquals(NOW.plusSeconds(120), third.nextAttemptAt());
    }

    @Test
    void stopsAfterConfiguredMaximumAttempt() {
        SourceRetryPolicy.RetryDecision decision = policy.decide(new IOException("still unavailable"), 5, NOW);

        assertFalse(decision.retryable());
        assertEquals(null, decision.nextAttemptAt());
    }

    @Test
    void doesNotRetryPermanentProcessingFailures() {
        DocumentProcessingException exception = new DocumentProcessingException(
                DocumentFailureCode.UNSUPPORTED_DOCUMENT_FORMAT);

        SourceRetryPolicy.RetryDecision decision = policy.decide(exception, 1, NOW);

        assertFalse(decision.retryable());
    }

    private SourceRecoveryProperties properties() {
        return new SourceRecoveryProperties(
                true, 5, Duration.ofSeconds(30), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(6), Duration.ofSeconds(30), 50);
    }
}
