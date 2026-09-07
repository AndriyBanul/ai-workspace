package com.aiworkspace.orchestrator.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.orchestrator.mappers.IngestionJobDetailsMapper;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import com.aiworkspace.orchestrator.repositories.IngestionJobRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionJobService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final IngestionJobRepository ingestionJobRepository;
    private final IngestionJobDetailsMapper detailsMapper;
    private final IngestionJobValidator ingestionJobValidator;

    public IngestionJobService(IngestionJobRepository ingestionJobRepository, IngestionJobDetailsMapper detailsMapper) {
        this(ingestionJobRepository, detailsMapper, new IngestionJobValidator());
    }

    @Autowired
    public IngestionJobService(
            IngestionJobRepository ingestionJobRepository,
            IngestionJobDetailsMapper detailsMapper,
            IngestionJobValidator ingestionJobValidator
    ) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.detailsMapper = detailsMapper;
        this.ingestionJobValidator = ingestionJobValidator;
    }

    @Transactional
    public IngestionJobDetails createJob(
            String workspaceId,
            List<IngestionContentType> submitted,
            List<IngestionContentType> skipped
    ) {
        ingestionJobValidator.validateWorkspaceId(workspaceId);

        Set<IngestionContentType> submittedTypes = Set.copyOf(submitted);
        Set<IngestionContentType> skippedTypes = Set.copyOf(skipped);
        Instant now = Instant.now();
        IngestionJobStatus status = submittedTypes.isEmpty() ? IngestionJobStatus.COMPLETED : IngestionJobStatus.RUNNING;
        String jobId = UUID.randomUUID().toString();

        IngestionJob job = new IngestionJob(
                jobId,
                workspaceId.trim(),
                status,
                now,
                now,
                status == IngestionJobStatus.COMPLETED ? now : null
        );

        List<IngestionJobStep> steps = Arrays.stream(IngestionContentType.values())
                .map(contentType -> new IngestionJobStep(
                        UUID.randomUUID().toString(),
                        jobId,
                        contentType,
                        stepStatus(contentType, submittedTypes, skippedTypes),
                        null,
                        skippedTypes.contains(contentType) ? now : null,
                        null,
                        null
                ))
                .toList();

        ingestionJobRepository.create(job, steps);
        return details(job, steps);
    }

    @Transactional(readOnly = true)
    public IngestionJobDetails getJob(String jobId) {
        ingestionJobValidator.validateJobId(jobId);
        IngestionJob job = ingestionJobRepository.findJob(jobId.trim())
                .orElseThrow(() -> new NoSuchElementException("Ingestion job was not found"));
        return details(job, ingestionJobRepository.findSteps(job.id()));
    }

    @Transactional
    public boolean markStepRunning(String jobId, IngestionContentType contentType) {
        ingestionJobValidator.validateJobId(jobId);
        Objects.requireNonNull(contentType, "contentType must not be null");
        Instant now = Instant.now();
        boolean updated = ingestionJobRepository.updateStepStatus(
                jobId.trim(),
                contentType,
                IngestionStepStatus.RUNNING,
                now,
                null,
                null,
                null
        );
        if (!updated) {
            return false;
        }

        recalculateJobStatus(jobId);
        return true;
    }

    @Transactional
    public boolean markStepCompleted(String jobId, IngestionContentType contentType) {
        ingestionJobValidator.validateJobId(jobId);
        Objects.requireNonNull(contentType, "contentType must not be null");
        Instant now = Instant.now();
        boolean updated = ingestionJobRepository.updateStepStatus(
                jobId.trim(),
                contentType,
                IngestionStepStatus.COMPLETED,
                null,
                now,
                null,
                null
        );
        if (!updated) {
            return false;
        }

        recalculateJobStatus(jobId);
        return true;
    }

    @Transactional
    public boolean markStepFailed(String jobId, IngestionContentType contentType, Exception exception) {
        ingestionJobValidator.validateJobId(jobId);
        Objects.requireNonNull(contentType, "contentType must not be null");
        Instant now = Instant.now();
        boolean updated = ingestionJobRepository.updateStepStatus(
                jobId.trim(),
                contentType,
                IngestionStepStatus.FAILED,
                null,
                now,
                errorMessage(exception),
                exception instanceof DocumentProcessingException documentException
                        ? documentException.code().name()
                        : null
        );
        if (!updated) {
            return false;
        }

        recalculateJobStatus(jobId);
        return true;
    }

    private void recalculateJobStatus(String jobId) {
        ingestionJobValidator.validateJobId(jobId);
        List<IngestionJobStep> steps = ingestionJobRepository.findSteps(jobId.trim());
        List<IngestionJobStep> submittedSteps = steps.stream()
                .filter(step -> step.status() != IngestionStepStatus.SKIPPED)
                .toList();

        IngestionJobStatus status;
        if (submittedSteps.isEmpty()) {
            status = IngestionJobStatus.COMPLETED;
        } else if (submittedSteps.stream().allMatch(step -> step.status() == IngestionStepStatus.COMPLETED)) {
            status = IngestionJobStatus.COMPLETED;
        } else if (submittedSteps.stream().allMatch(step -> step.status() == IngestionStepStatus.FAILED)) {
            status = IngestionJobStatus.FAILED;
        } else if (submittedSteps.stream().anyMatch(step -> step.status() == IngestionStepStatus.FAILED)
                && submittedSteps.stream().noneMatch(this::isActive)) {
            status = IngestionJobStatus.PARTIALLY_FAILED;
        } else {
            status = IngestionJobStatus.RUNNING;
        }

        Instant now = Instant.now();
        Instant completedAt = isTerminal(status) ? now : null;
        ingestionJobRepository.updateJobStatus(jobId.trim(), status, now, completedAt);
    }

    private IngestionStepStatus stepStatus(
            IngestionContentType contentType,
            Set<IngestionContentType> submitted,
            Set<IngestionContentType> skipped
    ) {
        if (submitted.contains(contentType)) {
            return IngestionStepStatus.PENDING;
        }

        if (skipped.contains(contentType)) {
            return IngestionStepStatus.SKIPPED;
        }

        return IngestionStepStatus.SKIPPED;
    }

    private IngestionJobDetails details(IngestionJob job, List<IngestionJobStep> steps) {
        return detailsMapper.toDetails(
                job,
                steps.stream()
                        .sorted(Comparator.comparing(IngestionJobStep::contentType))
                        .map(detailsMapper::toDetails)
                        .toList()
        );
    }

    private boolean isActive(IngestionJobStep step) {
        return step.status() == IngestionStepStatus.PENDING || step.status() == IngestionStepStatus.RUNNING;
    }

    private boolean isTerminal(IngestionJobStatus status) {
        return status == IngestionJobStatus.COMPLETED
                || status == IngestionJobStatus.PARTIALLY_FAILED
                || status == IngestionJobStatus.FAILED;
    }

    private String errorMessage(Exception exception) {
        if (exception == null) {
            return "Unknown error";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
