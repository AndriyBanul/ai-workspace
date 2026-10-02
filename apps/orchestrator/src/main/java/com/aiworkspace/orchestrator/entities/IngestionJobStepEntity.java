package com.aiworkspace.orchestrator.entities;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "ingestion_job_steps")
public class IngestionJobStepEntity {

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

    @Column(name = "error_code", length = 64)
    private String errorCode;

    public void updateStatus(
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            String errorCode
    ) {
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }
}
