package com.aiworkspace.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class InMemoryRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private static final long CLEANUP_INTERVAL = 1_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision acquire(String key, int limit) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate-limit key must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }

        long epochSecond = Instant.now(clock).getEpochSecond();
        long windowStart = epochSecond - Math.floorMod(epochSecond, WINDOW_SECONDS);
        Window updated = windows.compute(key, (ignored, current) -> {
            if (current == null || current.startedAtEpochSecond() != windowStart) {
                return new Window(windowStart, 1);
            }
            return new Window(windowStart, Math.min(Integer.MAX_VALUE, current.count() + 1));
        });

        if (requestCount.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().startedAtEpochSecond() < windowStart);
        }

        long retryAfter = Math.max(1, windowStart + WINDOW_SECONDS - epochSecond);
        return new RateLimitDecision(updated.count() <= limit, retryAfter);
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startedAtEpochSecond, int count) {
    }
}
