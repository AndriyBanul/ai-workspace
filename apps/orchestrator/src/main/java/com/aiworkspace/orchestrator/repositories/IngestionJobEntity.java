package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "ingestion_jobs")
public class IngestionJobEntity {

    @Id
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionJobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    void updateStatus(IngestionJobStatus status, Instant updatedAt, Instant completedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }
}
