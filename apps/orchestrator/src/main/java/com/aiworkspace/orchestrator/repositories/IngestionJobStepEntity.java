package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;

@Entity
@Getter(AccessLevel.PACKAGE)
@Table(name = "ingestion_job_steps")
class IngestionJobStepEntity {

    @Id
    private String id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private IngestionContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStepStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected IngestionJobStepEntity() {
    }

    IngestionJobStepEntity(
            String id,
            String jobId,
            IngestionContentType contentType,
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        this.id = id;
        this.jobId = jobId;
        this.contentType = contentType;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    void updateStatus(
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
}
