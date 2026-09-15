package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import org.springframework.stereotype.Service;

/** Owns the state transitions shared by every source ingestion path. */
@Service
public class SourceLifecycleCoordinator {

    private final IngestionJobService ingestionJobService;
    private final WorkspaceFileService workspaceFileService;

    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService) {
        this.ingestionJobService = ingestionJobService;
        this.workspaceFileService = workspaceFileService;
    }

    public <T> T process(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            SourceProcessingOperation<T> operation) throws Exception {
        if (!ingestionJobService.markStepRunning(job.jobId(), contentType)) {
            throw new IllegalStateException("Ingestion source is no longer pending");
        }
        boolean sourceTransitioned = false;
        try {
            workspaceFileService.markProcessing(job.workspaceId(), source.id());
            sourceTransitioned = true;
            T result = operation.process();
            if (ingestionJobService.markStepCompleted(job.jobId(), contentType)) {
                workspaceFileService.markProcessed(job.workspaceId(), source.id());
            }
            return result;
        } catch (Exception exception) {
            if (ingestionJobService.markStepFailed(job.jobId(), contentType, exception) && sourceTransitioned) {
                workspaceFileService.markFailed(job.workspaceId(), source.id());
            }
            throw exception;
        }
    }

    @FunctionalInterface
    public interface SourceProcessingOperation<T> {

        T process() throws Exception;
    }
}
