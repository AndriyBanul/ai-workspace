package com.aiworkspace.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_daily_quota_usage")
public class UserQuotaUsageEntity {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String action;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(nullable = false)
    private int consumed;

    protected UserQuotaUsageEntity() {
    }

    public UserQuotaUsageEntity(String id, String userId, String action, Instant windowStart) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.windowStart = windowStart;
    }

    public void consume() {
        consumed++;
    }

    public int getConsumed() {
        return consumed;
    }
}
