package com.aiworkspace.models;

import java.time.Instant;

import lombok.Builder;

@Builder
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String detail,
        String path
) {
}
