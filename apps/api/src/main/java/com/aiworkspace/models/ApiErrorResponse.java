package com.aiworkspace.models;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String detail,
        String path
) {
}
