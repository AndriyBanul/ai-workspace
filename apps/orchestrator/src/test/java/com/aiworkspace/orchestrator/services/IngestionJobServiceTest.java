package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.mappers.IngestionJobDetailsMapperImpl;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import com.aiworkspace.orchestrator.repositories.IngestionJobRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionJobServiceTest {

    @Test
    void createsJobWithSubmittedAndSkippedSteps() {
        CapturingRepository repository = new CapturingRepository();
        IngestionJobService service = newService(repository);

        var job = service.createJob(
                " workspace-1 ",
                List.of(IngestionContentType.DOCUMENTS, IngestionContentType.AUDIO),
                List.of(IngestionContentType.IMAGES, IngestionContentType.VIDEOS)
        );

        assertNotNull(job.jobId());
        assertEquals("workspace-1", job.workspaceId());
        assertEquals(IngestionJobStatus.RUNNING, job.status());
        assertEquals(IngestionStepStatus.PENDING, repository.step(job.jobId(), IngestionContentType.DOCUMENTS).status());
        assertEquals(IngestionStepStatus.SKIPPED, repository.step(job.jobId(), IngestionContentType.IMAGES).status());
    }

    @Test
    void marksCompletedJobWhenAllSubmittedStepsComplete() {
        CapturingRepository repository = new CapturingRepository();
        IngestionJobService service = newService(repository);
        var job = service.createJob(
                "workspace-1",
                List.of(IngestionContentType.DOCUMENTS),
                List.of(IngestionContentType.AUDIO, IngestionContentType.IMAGES, IngestionContentType.VIDEOS)
        );

        service.markStepRunning(job.jobId(), IngestionContentType.DOCUMENTS);
        service.markStepCompleted(job.jobId(), IngestionContentType.DOCUMENTS);

        assertEquals(IngestionJobStatus.COMPLETED, repository.jobs.get(job.jobId()).status());
        assertEquals(IngestionStepStatus.COMPLETED, repository.step(job.jobId(), IngestionContentType.DOCUMENTS).status());
        assertNotNull(repository.jobs.get(job.jobId()).completedAt());
    }

    @Test
    void marksPartiallyFailedJobWhenACompletedStepAndAFailedStepFinish() {
        CapturingRepository repository = new CapturingRepository();
        IngestionJobService service = newService(repository);
        var job = service.createJob(
                "workspace-1",
                List.of(IngestionContentType.DOCUMENTS, IngestionContentType.AUDIO),
                List.of(IngestionContentType.IMAGES, IngestionContentType.VIDEOS)
        );

        service.markStepCompleted(job.jobId(), IngestionContentType.DOCUMENTS);
        service.markStepFailed(job.jobId(), IngestionContentType.AUDIO, new RuntimeException("Audio failed"));

        assertEquals(IngestionJobStatus.PARTIALLY_FAILED, repository.jobs.get(job.jobId()).status());
        assertEquals("Audio failed", repository.step(job.jobId(), IngestionContentType.AUDIO).errorMessage());
    }

    @Test
    void doesNotOverwriteTerminalStepStatus() {
        CapturingRepository repository = new CapturingRepository();
        IngestionJobService service = newService(repository);
        var job = service.createJob(
                "workspace-1",
                List.of(IngestionContentType.DOCUMENTS),
                List.of(IngestionContentType.AUDIO, IngestionContentType.IMAGES, IngestionContentType.VIDEOS)
        );

        service.markStepFailed(job.jobId(), IngestionContentType.DOCUMENTS, new RuntimeException("First failure"));
        boolean completed = service.markStepCompleted(job.jobId(), IngestionContentType.DOCUMENTS);

        IngestionJobStep step = repository.step(job.jobId(), IngestionContentType.DOCUMENTS);
        assertFalse(completed);
        assertEquals(IngestionStepStatus.FAILED, step.status());
        assertEquals("First failure", step.errorMessage());
    }

    @Test
    void truncatesLongErrorMessages() {
        CapturingRepository repository = new CapturingRepository();
        IngestionJobService service = newService(repository);
        var job = service.createJob(
                "workspace-1",
                List.of(IngestionContentType.DOCUMENTS),
                List.of(IngestionContentType.AUDIO, IngestionContentType.IMAGES, IngestionContentType.VIDEOS)
        );

        service.markStepFailed(job.jobId(), IngestionContentType.DOCUMENTS, new RuntimeException("x".repeat(1_200)));

        assertEquals(1_000, repository.step(job.jobId(), IngestionContentType.DOCUMENTS).errorMessage().length());
    }

    @Test
    void rejectsBlankJobId() {
        IngestionJobService service = newService(new CapturingRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getJob(" ")
        );

        assertEquals("Ingestion job ID must not be blank", exception.getMessage());
    }

    @Test
    void failsWhenJobIsMissing() {
        IngestionJobService service = newService(new CapturingRepository());

        assertThrows(NoSuchElementException.class, () -> service.getJob("missing"));
    }

    private static class CapturingRepository implements IngestionJobRepository {

        private final Map<String, IngestionJob> jobs = new HashMap<>();
        private final Map<String, EnumMap<IngestionContentType, IngestionJobStep>> steps = new HashMap<>();

        @Override
        public void create(IngestionJob job, List<IngestionJobStep> steps) {
            jobs.put(job.id(), job);
            EnumMap<IngestionContentType, IngestionJobStep> byType = new EnumMap<>(IngestionContentType.class);
            for (IngestionJobStep step : steps) {
                byType.put(step.contentType(), step);
            }
            this.steps.put(job.id(), byType);
        }

        @Override
        public Optional<IngestionJob> findJob(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public List<IngestionJobStep> findSteps(String jobId) {
            EnumMap<IngestionContentType, IngestionJobStep> stepsByType = steps.get(jobId);
            return stepsByType == null ? List.of() : List.copyOf(stepsByType.values());
        }

        @Override
        public void updateJobStatus(String jobId, IngestionJobStatus status, Instant updatedAt, Instant completedAt) {
            IngestionJob job = jobs.get(jobId);
            jobs.put(jobId, new IngestionJob(
                    job.id(),
                    job.workspaceId(),
                    status,
                    job.createdAt(),
                    updatedAt,
                    completedAt
            ));
        }

        @Override
        public boolean updateStepStatus(
                String jobId,
                IngestionContentType contentType,
                IngestionStepStatus status,
                Instant startedAt,
                Instant completedAt,
                String errorMessage
        ) {
            IngestionJobStep existing = step(jobId, contentType);
            if (existing.status() == IngestionStepStatus.COMPLETED
                    || existing.status() == IngestionStepStatus.FAILED
                    || existing.status() == IngestionStepStatus.SKIPPED) {
                return false;
            }

            steps.get(jobId).put(contentType, new IngestionJobStep(
                    existing.id(),
                    existing.jobId(),
                    existing.contentType(),
                    status,
                    startedAt == null ? existing.startedAt() : startedAt,
                    completedAt == null ? existing.completedAt() : completedAt,
                    errorMessage == null ? existing.errorMessage() : errorMessage
            ));
            return true;
        }

        private IngestionJobStep step(String jobId, IngestionContentType contentType) {
            IngestionJobStep step = steps.get(jobId).get(contentType);
            assertTrue(step != null);
            return step;
        }
    }

    private static IngestionJobService newService(CapturingRepository repository) {
        return new IngestionJobService(repository, new IngestionJobDetailsMapperImpl());
    }
}
