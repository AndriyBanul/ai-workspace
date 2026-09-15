package com.aiworkspace.orchestrator.entities;

import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "source_recovery_tasks")
public class SourceRecoveryTaskEntity {

    @Id
    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private SourceOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceRecoveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void start(SourceOperationType operationType, Instant now, Instant leaseExpiresAt, boolean resetAttempts) {
        this.operationType = operationType;
        this.status = SourceRecoveryStatus.RUNNING;
        this.attemptCount = (resetAttempts ? 0 : attemptCount) + 1;
        this.nextAttemptAt = null;
        this.leaseExpiresAt = leaseExpiresAt;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void schedule(Instant nextAttemptAt, String errorCode, String errorMessage, Instant now) {
        this.status = SourceRecoveryStatus.SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseExpiresAt = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = now;
    }

    public void complete(Instant nextReconciliationAt, Instant now) {
        this.status = SourceRecoveryStatus.COMPLETED;
        this.nextAttemptAt = nextReconciliationAt;
        this.leaseExpiresAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void deadLetter(String errorCode, String errorMessage, Instant now) {
        this.status = SourceRecoveryStatus.DEAD_LETTER;
        this.nextAttemptAt = null;
        this.leaseExpiresAt = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = now;
    }

    public void prepare(SourceOperationType operationType, Instant nextAttemptAt, Instant now) {
        this.operationType = operationType;
        this.status = SourceRecoveryStatus.SCHEDULED;
        this.attemptCount = 0;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseExpiresAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }
}
