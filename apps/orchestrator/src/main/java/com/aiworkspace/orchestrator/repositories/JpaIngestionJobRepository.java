package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.IngestionJobEntity;
import com.aiworkspace.orchestrator.mappers.IngestionJobEntityMapper;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class JpaIngestionJobRepository implements IngestionJobRepository {

    private static final Collection<IngestionStepStatus> ACTIVE_STEP_STATUSES = List.of(
            IngestionStepStatus.PENDING,
            IngestionStepStatus.RUNNING
    );

    private final JpaIngestionJobEntityRepository jobRepository;
    private final JpaIngestionJobStepEntityRepository stepRepository;
    private final IngestionJobEntityMapper mapper;

    @Override
    public void create(IngestionJob job, List<IngestionJobStep> steps) {
        jobRepository.save(mapper.toEntity(job));
        stepRepository.saveAll(steps.stream()
                .map(mapper::toEntity)
                .toList());
    }

    @Override
    public Optional<IngestionJob> findJob(String jobId) {
        return jobRepository.findById(jobId)
                .map(mapper::toModel);
    }

    @Override
    public List<IngestionJob> findRecentJobs(String workspaceId, int limit) {
        return jobRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, limit))
                .stream().map(mapper::toModel).toList();
    }

    @Override
    public List<IngestionJobStep> findSteps(String jobId) {
        return stepRepository.findByJobId(jobId).stream()
                .map(mapper::toModel)
                .toList();
    }

    @Override
    public void updateJobStatus(String jobId, IngestionJobStatus status, Instant updatedAt, Instant completedAt) {
        IngestionJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Ingestion job was not found"));
        job.updateStatus(status, updatedAt, completedAt);
        jobRepository.save(job);
    }

    @Override
    public boolean updateStepStatus(
            String jobId,
            IngestionContentType contentType,
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            String errorCode
    ) {
        jobRepository.lockById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Ingestion job was not found"));
        int updatedRows = switch (status) {
            case RUNNING -> stepRepository.markRunningIfActive(
                    jobId,
                    contentType,
                    status,
                    startedAt,
                    ACTIVE_STEP_STATUSES
            );
            case COMPLETED -> stepRepository.markCompletedIfActive(
                    jobId,
                    contentType,
                    status,
                    completedAt,
                    ACTIVE_STEP_STATUSES
            );
            case FAILED -> stepRepository.markFailedIfActive(
                    jobId,
                    contentType,
                    status,
                    completedAt,
                    errorMessage,
                    errorCode,
                    ACTIVE_STEP_STATUSES
            );
            case PENDING, SKIPPED -> throw new IllegalArgumentException("Unsupported ingestion step transition target");
        };

        if (updatedRows > 0) {
            return true;
        }

        stepRepository.findByJobIdAndContentType(jobId, contentType)
                .orElseThrow(() -> new NoSuchElementException("Ingestion job step was not found"));
        return false;
    }
}
