package com.aiworkspace.models;

public record QuotaDecision(boolean allowed, long retryAfterSeconds) {
}
