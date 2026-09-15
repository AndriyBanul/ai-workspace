package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceLifecycleCoordinatorTest {

    @Test
    void movesEverySuccessfulSourceThroughProcessingAndCompletion() throws Exception {
        IngestionJobService jobs = mock(IngestionJobService.class);
        WorkspaceFileService sources = mock(WorkspaceFileService.class);
        when(jobs.markStepRunning("job-1", IngestionContentType.AUDIO)).thenReturn(true);
        when(jobs.markStepCompleted("job-1", IngestionContentType.AUDIO)).thenReturn(true);
        SourceLifecycleCoordinator coordinator = new SourceLifecycleCoordinator(jobs, sources);

        String result = coordinator.process(job(), IngestionContentType.AUDIO, source(), () -> "indexed");

        assertEquals("indexed", result);
        InOrder order = inOrder(jobs, sources);
        order.verify(jobs).markStepRunning("job-1", IngestionContentType.AUDIO);
        order.verify(sources).markProcessing("workspace-1", "source-1");
        order.verify(jobs).markStepCompleted("job-1", IngestionContentType.AUDIO);
        order.verify(sources).markProcessed("workspace-1", "source-1");
    }

    @Test
    void marksBothJobAndSourceFailedWhenProcessingFails() {
        IngestionJobService jobs = mock(IngestionJobService.class);
        WorkspaceFileService sources = mock(WorkspaceFileService.class);
        when(jobs.markStepRunning("job-1", IngestionContentType.AUDIO)).thenReturn(true);
        when(jobs.markStepFailed(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(IngestionContentType.AUDIO),
                org.mockito.ArgumentMatchers.any(IOException.class))).thenReturn(true);
        SourceLifecycleCoordinator coordinator = new SourceLifecycleCoordinator(jobs, sources);

        assertThrows(IOException.class, () -> coordinator.process(
                job(), IngestionContentType.AUDIO, source(), () -> {
                    throw new IOException("provider unavailable");
                }));

        verify(sources).markFailed("workspace-1", "source-1");
    }

    private IngestionJobDetails job() {
        Instant now = Instant.now();
        return new IngestionJobDetails(
                "job-1", "workspace-1", IngestionJobStatus.RUNNING, now, now, null, List.of());
    }

    private WorkspaceFile source() {
        Instant now = Instant.now();
        return WorkspaceFile.builder()
                .id("source-1")
                .workspaceId("workspace-1")
                .originalFilename("audio.mp3")
                .sourceType(WorkspaceFileSourceType.AUDIO)
                .status(WorkspaceFileStatus.UPLOADED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
