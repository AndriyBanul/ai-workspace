package com.aiworkspace.knowledge.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SearchTelemetry {
    public static final SearchTelemetry NOOP = new SearchTelemetry(new CompositeMeterRegistry());
    private final MeterRegistry registry;

    public SearchTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @FunctionalInterface
    public interface Operation<T> {
        T run() throws IOException;
    }

    public <T> T measure(String operation, Operation<T> action) throws IOException {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return action.run();
        } catch (IOException | RuntimeException exception) {
            outcome = "failure";
            if (operation.startsWith("embedding.")) {
                registry.counter("knowledge.embedding.failures", "operation", operation).increment();
            }
            throw exception;
        } finally {
            registry.timer("knowledge.operation.duration", "operation", operation, "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public void fallback(String reason) {
        registry.counter("knowledge.search.fallbacks", "reason", reason).increment();
    }
}
