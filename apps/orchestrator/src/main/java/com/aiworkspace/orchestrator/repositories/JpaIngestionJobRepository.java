package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaIngestionJobRepository implements IngestionJobRepository {

    private final JpaIngestionJobEntityRepository jobRepository;
    private final JpaIngestionJobStepEntityRepository stepRepository;

    JpaIngestionJobRepository(
            JpaIngestionJobEntityRepository jobRepository,
            JpaIngestionJobStepEntityRepository stepRepository
    ) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
    }

    @Override
    public void create(IngestionJob job, List<IngestionJobStep> steps) {
        jobRepository.save(toEntity(job));
        stepRepository.saveAll(steps.stream()
                .map(this::toEntity)
                .toList());
    }

    @Override
    public Optional<IngestionJob> findJob(String jobId) {
        return jobRepository.findById(jobId)
                .map(this::toModel);
    }

    @Override
    public List<IngestionJobStep> findSteps(String jobId) {
        return stepRepository.findByJobId(jobId).stream()
                .map(this::toModel)
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
    public void updateStepStatus(
            String jobId,
            IngestionContentType contentType,
            IngestionStepStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        IngestionJobStepEntity step = stepRepository.findByJobIdAndContentType(jobId, contentType)
                .orElseThrow(() -> new NoSuchElementException("Ingestion job step was not found"));
        step.updateStatus(
                status,
                startedAt == null ? step.getStartedAt() : startedAt,
                completedAt,
                errorMessage
        );
        stepRepository.save(step);
    }

    private IngestionJobEntity toEntity(IngestionJob job) {
        return new IngestionJobEntity(
                job.id(),
                job.workspaceId(),
                job.status(),
                job.createdAt(),
                job.updatedAt(),
                job.completedAt()
        );
    }

    private IngestionJobStepEntity toEntity(IngestionJobStep step) {
        return new IngestionJobStepEntity(
                step.id(),
                step.jobId(),
                step.contentType(),
                step.status(),
                step.startedAt(),
                step.completedAt(),
                step.errorMessage()
        );
    }

    private IngestionJob toModel(IngestionJobEntity entity) {
        return new IngestionJob(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt()
        );
    }

    private IngestionJobStep toModel(IngestionJobStepEntity entity) {
        return new IngestionJobStep(
                entity.getId(),
                entity.getJobId(),
                entity.getContentType(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage()
        );
    }
}
