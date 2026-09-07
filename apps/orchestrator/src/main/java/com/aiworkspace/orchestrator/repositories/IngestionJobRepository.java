package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IngestionJobRepository {

    void create(IngestionJob job, List<IngestionJobStep> steps);

    Optional<IngestionJob> findJob(String jobId);

    List<IngestionJobStep> findSteps(String jobId);

    void updateJobStatus(String jobId, IngestionJobStatus status, Instant updatedAt, Instant completedAt);

    boolean updateStepStatus(
            String jobId,
            IngestionContentType contentType,
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            String errorCode
    );
}
