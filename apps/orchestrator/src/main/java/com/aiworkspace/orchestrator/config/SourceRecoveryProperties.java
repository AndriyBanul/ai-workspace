package com.aiworkspace.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai-workspace.orchestrator.recovery")
public record SourceRecoveryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration initialBackoff,
        @DefaultValue("15m") Duration maxBackoff,
        @DefaultValue("30m") Duration leaseDuration,
        @DefaultValue("6h") Duration reconciliationInterval,
        @DefaultValue("30s") Duration pollInterval,
        @DefaultValue("50") int batchSize
) {

    public SourceRecoveryProperties {
        if (maxAttempts < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Recovery max attempts and batch size must be positive");
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero() || maxBackoff.compareTo(initialBackoff) < 0
                || leaseDuration.isNegative() || leaseDuration.isZero()
                || reconciliationInterval.isNegative() || reconciliationInterval.isZero()
                || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("Recovery durations must be positive and consistently ordered");
        }
    }
}
