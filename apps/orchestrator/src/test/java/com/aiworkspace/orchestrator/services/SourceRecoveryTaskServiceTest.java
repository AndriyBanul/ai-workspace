package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.entities.SourceRecoveryTaskEntity;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.repositories.SourceRecoveryTaskRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceRecoveryTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @Test
    void recordsRunningAttemptAndSchedulesTransientFailure() {
        TestContext context = context(true);

        assertEquals(1, context.service().startAttempt(source(), SourceOperationType.PROCESS));
        context.service().fail(source(), SourceOperationType.PROCESS, new IOException("OpenSearch unavailable"));

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

        context.service().startAttempt(source(), SourceOperationType.PROCESS);
        context.service().complete(source(), SourceOperationType.PROCESS);

        SourceRecoveryTaskEntity task = context.saved().get();
        assertEquals(SourceRecoveryStatus.COMPLETED, task.getStatus());
        assertEquals(NOW.plus(Duration.ofHours(6)), task.getNextAttemptAt());
        assertNull(task.getLastErrorCode());
    }

    @Test
    void disabledRecoveryMovesFailureToDeadLetter() {
        TestContext context = context(false);

        context.service().startAttempt(source(), SourceOperationType.PROCESS);
        context.service().fail(source(), SourceOperationType.PROCESS, new IOException("OpenSearch unavailable"));

        SourceRecoveryTaskEntity task = context.saved().get();
        assertEquals(SourceRecoveryStatus.DEAD_LETTER, task.getStatus());
        assertNull(task.getNextAttemptAt());
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
