package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.models.SourceRecoveryTask;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceRecoverySchedulerTest {

    @Test
    void schedulesRepairWhenProcessedSourceHasNoIndexedKnowledge() throws IOException {
        TestContext context = context();
        WorkspaceFile source = source();
        when(context.files().getFile("workspace-1", "source-1")).thenReturn(source);
        when(context.files().contentExists("workspace-1", "source-1")).thenReturn(true);
        when(context.knowledge().hasSourceKnowledge("workspace-1", "source-1")).thenReturn(false);

        context.scheduler().recoverDueSources();

        verify(context.recovery()).scheduleRepair(
                source, "KNOWLEDGE_MISSING", "Indexed source knowledge is missing");
    }

    @Test
    void deadLettersSourceWhenStoredContentIsMissing() throws IOException {
        TestContext context = context();
        WorkspaceFile source = source();
        when(context.files().getFile("workspace-1", "source-1")).thenReturn(source);
        when(context.files().contentExists("workspace-1", "source-1")).thenReturn(false);

        context.scheduler().recoverDueSources();

        verify(context.recovery()).recordIntegrityFailure(
                source, "SOURCE_CONTENT_MISSING", "Stored source content is missing");
    }

    @Test
    void removesOrphanedKnowledgeWhenSourceMetadataIsGone() throws IOException {
        TestContext context = context();
        when(context.files().getFile("workspace-1", "source-1"))
                .thenThrow(new NoSuchElementException("missing"));

        context.scheduler().recoverDueSources();

        verify(context.knowledge()).deleteSourceKnowledge("workspace-1", "source-1");
        verify(context.recovery()).completeDeletedTask(task());
    }

    @Test
    void completesScheduledProcessingTaskWhenSourceWasDeleted() throws IOException {
        TestContext context = context(scheduledTask());
        doThrow(new NoSuchElementException("missing"))
                .when(context.orchestrator()).recoverSource("workspace-1", "source-1");

        context.scheduler().recoverDueSources();

        verify(context.knowledge()).deleteSourceKnowledge("workspace-1", "source-1");
        verify(context.recovery()).completeDeletedTask(scheduledTask());
    }

    private TestContext context() {
        return context(task());
    }

    private TestContext context(SourceRecoveryTask task) {
        SourceRecoveryTaskService recovery = mock(SourceRecoveryTaskService.class);
        OrchestratorService orchestrator = mock(OrchestratorService.class);
        WorkspaceLifecycleService lifecycle = mock(WorkspaceLifecycleService.class);
        WorkspaceFileService files = mock(WorkspaceFileService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SourceRecoveryProperties properties = new SourceRecoveryProperties(
                true, 5, Duration.ofSeconds(30), Duration.ofMinutes(15),
                Duration.ofMinutes(30), Duration.ofHours(6), Duration.ofSeconds(30), 50);
        when(recovery.findDueTasks()).thenReturn(List.of(task));
        SourceRecoveryScheduler scheduler = new SourceRecoveryScheduler(
                recovery, orchestrator, lifecycle, files, knowledge, properties);
        return new TestContext(scheduler, recovery, orchestrator, files, knowledge);
    }

    private SourceRecoveryTask task() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        return new SourceRecoveryTask(
                "source-1", "workspace-1", SourceOperationType.PROCESS, SourceRecoveryStatus.COMPLETED,
                1, now, null, null, null, now.minusSeconds(60), now.minusSeconds(30));
    }

    private SourceRecoveryTask scheduledTask() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        return new SourceRecoveryTask(
                "source-1", "workspace-1", SourceOperationType.PROCESS, SourceRecoveryStatus.SCHEDULED,
                1, now, null, "IOException", "temporary", now.minusSeconds(60), now.minusSeconds(30));
    }

    private WorkspaceFile source() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        return WorkspaceFile.builder()
                .id("source-1")
                .workspaceId("workspace-1")
                .originalFilename("report.pdf")
                .storageKey("workspace-1/source-1")
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .status(WorkspaceFileStatus.PROCESSED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private record TestContext(
            SourceRecoveryScheduler scheduler,
            SourceRecoveryTaskService recovery,
            OrchestratorService orchestrator,
            WorkspaceFileService files,
            KnowledgeService knowledge
    ) {
    }
}
