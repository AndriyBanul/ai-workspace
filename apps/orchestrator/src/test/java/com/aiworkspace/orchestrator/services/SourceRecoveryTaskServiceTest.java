package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.entities.SourceRecoveryTaskEntity;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.repositories.SourceRecoveryTaskRepository;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceRecoveryTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @Test
    void persistsPendingSubmissionWithItsJobBeforeAWorkerClaimsIt() {
        TestContext context = context(true);

        context.service().queueSubmission(source(), "job-1");

        SourceRecoveryTaskEntity pending = context.saved().get();
        assertEquals(SourceRecoveryStatus.SCHEDULED, pending.getStatus());
        assertEquals("job-1", pending.getJobId());
        assertEquals(0, pending.getAttemptCount());
        assertEquals(NOW, pending.getNextAttemptAt());
        assertThrows(WorkspaceSourceConflictException.class,
                () -> context.service().queueSubmission(source(), "job-2"));
        assertThrows(WorkspaceSourceConflictException.class,
                () -> context.service().startAttempt(source(), SourceOperationType.DELETE));

        context.service().startAttempt(source(), SourceOperationType.PROCESS);
        assertEquals(SourceRecoveryStatus.RUNNING, pending.getStatus());
        assertEquals("job-1", pending.getJobId());
    }

    @Test
    void recordsRunningAttemptAndSchedulesTransientFailure() {
        TestContext context = context(true);

        RecoveryClaim claim = context.service().startAttempt(source(), SourceOperationType.PROCESS);
        assertEquals(1, claim.attempt());
        context.service().fail(source(), SourceOperationType.PROCESS, claim,
                new IOException("OpenSearch unavailable"));

        SourceRecoveryTaskEntity task = context.saved().get();
        assertEquals(SourceRecoveryStatus.SCHEDULED, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertEquals(NOW.plusSeconds(30), task.getNextAttemptAt());
        assertNull(task.getLeaseExpiresAt());
        assertEquals("IOException", task.getLastErrorCode());
    }

    @Test
    void completionSchedulesFutureIntegrityReconciliation() {
        TestContext context = context(true);

        RecoveryClaim claim = context.service().startAttempt(source(), SourceOperationType.PROCESS);
        context.service().complete(source(), SourceOperationType.PROCESS, claim);

        SourceRecoveryTaskEntity task = context.saved().get();
        assertEquals(SourceRecoveryStatus.COMPLETED, task.getStatus());
        assertEquals(NOW.plus(Duration.ofHours(6)), task.getNextAttemptAt());
        assertNull(task.getLastErrorCode());
    }

    @Test
    void disabledRecoveryMovesFailureToDeadLetter() {
        TestContext context = context(false);

        RecoveryClaim claim = context.service().startAttempt(source(), SourceOperationType.PROCESS);
        context.service().fail(source(), SourceOperationType.PROCESS, claim,
                new IOException("OpenSearch unavailable"));

        SourceRecoveryTaskEntity task = context.saved().get();
        assertEquals(SourceRecoveryStatus.DEAD_LETTER, task.getStatus());
        assertNull(task.getNextAttemptAt());
    }

    @Test
    void newerClaimFencesAWorkerFromAnotherInstanceAfterLeaseExpiry() {
        SourceRecoveryTaskRepository repository = mock(SourceRecoveryTaskRepository.class);
        AtomicReference<SourceRecoveryTaskEntity> saved = new AtomicReference<>();
        AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
        when(repository.findForUpdate("source-1")).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(repository.save(any(SourceRecoveryTaskEntity.class))).thenAnswer(invocation -> {
            SourceRecoveryTaskEntity task = invocation.getArgument(0);
            saved.set(task);
            return task;
        });
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };
        SourceRecoveryProperties properties = new SourceRecoveryProperties(
                true, 5, Duration.ofSeconds(30), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(6), Duration.ofSeconds(30), 50);
        SourceRetryPolicy retryPolicy = new SourceRetryPolicy(properties);
        SourceRecoveryTaskService firstInstance = new SourceRecoveryTaskService(
                repository, retryPolicy, properties, clock);
        SourceRecoveryTaskService secondInstance = new SourceRecoveryTaskService(
                repository, retryPolicy, properties, clock);

        RecoveryClaim first = firstInstance.startAttempt(source(), SourceOperationType.PROCESS);
        currentTime.set(NOW.plus(Duration.ofMinutes(31)));
        RecoveryClaim second = secondInstance.startAttempt(source(), SourceOperationType.PROCESS);

        assertEquals(2, second.attempt());
        assertThrows(WorkspaceSourceConflictException.class,
                () -> firstInstance.complete(source(), SourceOperationType.PROCESS, first));
        assertThrows(WorkspaceSourceConflictException.class,
                () -> firstInstance.fail(source(), SourceOperationType.PROCESS, first, new IOException("late failure")));
        assertThrows(WorkspaceSourceConflictException.class,
                () -> firstInstance.renewLease(source(), SourceOperationType.PROCESS, first));
        secondInstance.renewLease(source(), SourceOperationType.PROCESS, second);
        assertEquals(currentTime.get().plus(Duration.ofMinutes(30)), saved.get().getLeaseExpiresAt());
        secondInstance.complete(source(), SourceOperationType.PROCESS, second);
        assertEquals(SourceRecoveryStatus.COMPLETED, saved.get().getStatus());
    }

    private TestContext context(boolean enabled) {
        SourceRecoveryTaskRepository repository = mock(SourceRecoveryTaskRepository.class);
        AtomicReference<SourceRecoveryTaskEntity> saved = new AtomicReference<>();
        when(repository.findForUpdate("source-1")).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(repository.save(any(SourceRecoveryTaskEntity.class))).thenAnswer(invocation -> {
            SourceRecoveryTaskEntity task = invocation.getArgument(0);
            saved.set(task);
            return task;
        });
        SourceRecoveryProperties properties = new SourceRecoveryProperties(
                enabled, 5, Duration.ofSeconds(30), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(6), Duration.ofSeconds(30), 50);
        SourceRetryPolicy retryPolicy = new SourceRetryPolicy(properties);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new TestContext(new SourceRecoveryTaskService(repository, retryPolicy, properties, clock), saved);
    }

    private WorkspaceFile source() {
        return WorkspaceFile.builder()
                .id("source-1")
                .workspaceId("workspace-1")
                .originalFilename("report.pdf")
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .status(WorkspaceFileStatus.UPLOADED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private record TestContext(SourceRecoveryTaskService service, AtomicReference<SourceRecoveryTaskEntity> saved) {
    }
}
