package com.aiworkspace.models;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
