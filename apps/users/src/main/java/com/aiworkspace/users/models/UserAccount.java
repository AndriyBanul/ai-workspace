package com.aiworkspace.users.models;

import java.time.Instant;
import lombok.Builder;

@Builder
public record UserAccount(
        String id,
        String email,
        String displayName,
        Instant createdAt
) {
}
