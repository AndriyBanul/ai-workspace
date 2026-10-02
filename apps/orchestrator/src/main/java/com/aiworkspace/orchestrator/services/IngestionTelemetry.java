package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.SourceOperationType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class IngestionTelemetry {

    public static final IngestionTelemetry NOOP = new IngestionTelemetry(new CompositeMeterRegistry());
    private final MeterRegistry registry;

    public IngestionTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAttempt(SourceOperationType operation, String outcome, long elapsedNanos) {
        registry.counter("source.operation.attempts", "operation", operation.name().toLowerCase(),
                "outcome", outcome).increment();
        registry.timer("source.operation.duration", "operation", operation.name().toLowerCase(),
                "outcome", outcome).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRecovery(String outcome) {
        registry.counter("source.recovery.transitions", "outcome", outcome).increment();
    }

    public void registerWorkerQueue(ThreadPoolExecutor executor) {
        Gauge.builder("source.worker.queue.depth", executor, pool -> pool.getQueue().size())
                .register(registry);
        Gauge.builder("source.worker.active", executor, ThreadPoolExecutor::getActiveCount)
                .register(registry);
    }
}
