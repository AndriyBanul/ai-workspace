package com.aiworkspace.models;

public record RateLimitWindow(long startedAtEpochSecond, int count) {
}
