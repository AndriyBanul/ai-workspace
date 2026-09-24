package com.aiworkspace.orchestrator.models;

/** Identifies one leased attempt; a later retry must never be completed by an older worker. */
public record RecoveryClaim(String token, int attempt) {
}
