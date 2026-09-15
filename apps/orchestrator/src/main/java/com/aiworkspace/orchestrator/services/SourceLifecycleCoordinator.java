package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Owns the state transitions shared by every source ingestion path. */
@Service
public class SourceLifecycleCoordinator {

    private final IngestionJobService ingestionJobService;
    private final WorkspaceFileService workspaceFileService;
    private final SourceRecoveryTracker recoveryTracker;

    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService) {
        this(ingestionJobService, workspaceFileService, SourceRecoveryTracker.noop());
    }

    @Autowired
    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker) {
        this.ingestionJobService = ingestionJobService;
        this.workspaceFileService = workspaceFileService;
        this.recoveryTracker = recoveryTracker;
    }

    public <T> T process(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            SourceProcessingOperation<T> operation) throws Exception {
        return process(job, contentType, source, operation, false);
    }

    public void startRecoveryAttempt(WorkspaceFile source) {
        recoveryTracker.startAttempt(source, SourceOperationType.PROCESS);
    }

    public void failRecoveryAttempt(WorkspaceFile source, Exception exception) {
        recoveryTracker.fail(source, SourceOperationType.PROCESS, exception);
    }

    public <T> T processClaimedRecovery(IngestionJobDetails job, IngestionContentType contentType,
            WorkspaceFile source, SourceProcessingOperation<T> operation) throws Exception {
        return process(job, contentType, source, operation, true);
    }

    private <T> T process(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            SourceProcessingOperation<T> operation, boolean recoveryStarted) throws Exception {
        boolean sourceTransitioned = false;
        try {
            if (!ingestionJobService.markStepRunning(job.jobId(), contentType)) {
                throw new IllegalStateException("Ingestion source is no longer pending");
            }
            if (!recoveryStarted) {
                recoveryTracker.startAttempt(source, SourceOperationType.PROCESS);
                recoveryStarted = true;
            }
            workspaceFileService.markProcessing(job.workspaceId(), source.id());
            sourceTransitioned = true;
            T result = operation.process();
            if (!ingestionJobService.markStepCompleted(job.jobId(), contentType)) {
                throw new IllegalStateException("Ingestion source is no longer running");
            }
            workspaceFileService.markProcessed(job.workspaceId(), source.id());
            recoveryTracker.complete(source, SourceOperationType.PROCESS);
            return result;
        } catch (Exception exception) {
            try {
                ingestionJobService.markStepFailed(job.jobId(), contentType, exception);
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
            }
            if (sourceTransitioned) {
                try {
                    workspaceFileService.markFailed(job.workspaceId(), source.id());
                } catch (RuntimeException statusException) {
                    exception.addSuppressed(statusException);
                }
            }
            if (recoveryStarted) {
                try {
                    recoveryTracker.fail(source, SourceOperationType.PROCESS, exception);
                } catch (RuntimeException recoveryException) {
                    exception.addSuppressed(recoveryException);
                }
            }
            throw exception;
        }
    }

    @FunctionalInterface
    public interface SourceProcessingOperation<T> {

        T process() throws Exception;
    }
}
