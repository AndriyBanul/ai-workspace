package com.aiworkspace.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

import lombok.Builder;

@Builder
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String detail,
        String path,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {
}
