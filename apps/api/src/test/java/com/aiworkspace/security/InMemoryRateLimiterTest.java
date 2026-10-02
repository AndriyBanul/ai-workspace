package com.aiworkspace.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    @Test
    void deniesRequestsAfterLimitWithinFixedWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(
                Clock.fixed(Instant.parse("2026-09-15T12:00:10Z"), ZoneOffset.UTC));

        assertTrue(limiter.acquire("api:client-1", 2).allowed());
        assertTrue(limiter.acquire("api:client-1", 2).allowed());
        InMemoryRateLimiter.RateLimitDecision denied = limiter.acquire("api:client-1", 2);

        assertFalse(denied.allowed());
        assertEquals(50, denied.retryAfterSeconds());
        assertTrue(limiter.acquire("api:client-2", 2).allowed());
    }
}
