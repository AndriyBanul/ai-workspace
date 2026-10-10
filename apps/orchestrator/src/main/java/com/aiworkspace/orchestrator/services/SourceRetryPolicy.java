package com.aiworkspace.orchestrator.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.models.RetryDecision;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SourceRetryPolicy {

    private final SourceRecoveryProperties properties;

    public RetryDecision decide(Exception exception, int attemptCount, Instant now) {
        if (!isRetryable(exception) || attemptCount >= properties.maxAttempts()) {
            return new RetryDecision(false, null);
        }
        long multiplier = 1L << Math.min(20, Math.max(0, attemptCount - 1));
        Duration delay = properties.initialBackoff().multipliedBy(multiplier);
        if (delay.compareTo(properties.maxBackoff()) > 0) {
            delay = properties.maxBackoff();
        }
        return new RetryDecision(true, now.plus(delay));
    }

    private boolean isRetryable(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof DocumentProcessingException || current instanceof IllegalArgumentException
                    || current instanceof NoSuchElementException || current instanceof FileNotFoundException
                    || current instanceof NoSuchFileException) {
                return false;
            }
            if (current instanceof UpstreamServiceException || current instanceof IOException
                    || current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
