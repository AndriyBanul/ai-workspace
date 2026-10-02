package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceProcessingResult;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Owns the state transitions shared by every source ingestion path. */
@Service
public class SourceLifecycleCoordinator {

    private final IngestionJobService ingestionJobService;
    private final WorkspaceFileService workspaceFileService;
    private final SourceRecoveryTracker recoveryTracker;
    private final SourceCompletionService completionService;
    private final SourceLeaseKeeper leaseKeeper;
    private final IngestionTelemetry telemetry;

    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService) {
        this(ingestionJobService, workspaceFileService, SourceRecoveryTracker.noop());
    }

    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker) {
        this(ingestionJobService, workspaceFileService, recoveryTracker,
                new SourceCompletionService(ingestionJobService, workspaceFileService, recoveryTracker), null,
                IngestionTelemetry.NOOP);
    }

    @Autowired
    public SourceLifecycleCoordinator(IngestionJobService ingestionJobService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker,
            SourceCompletionService completionService, SourceLeaseKeeper leaseKeeper,
            IngestionTelemetry telemetry) {
        this.ingestionJobService = ingestionJobService;
        this.workspaceFileService = workspaceFileService;
        this.recoveryTracker = recoveryTracker;
        this.completionService = completionService;
        this.leaseKeeper = leaseKeeper;
        this.telemetry = telemetry;
    }

    public <T> T process(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            SourceProcessingOperation<T> operation) throws Exception {
        return process(job, contentType, source, operation, null);
    }

    @Transactional
    public IngestionJobDetails queueSubmission(String workspaceId, List<IngestionContentType> submittedTypes,
            List<IngestionContentType> skippedTypes, List<WorkspaceFile> sources) {
        IngestionJobDetails job = ingestionJobService.createJob(workspaceId, submittedTypes, skippedTypes);
        sources.forEach(source -> recoveryTracker.queueSubmission(source, job.jobId()));
        return job;
    }

    public RecoveryClaim startRecoveryAttempt(WorkspaceFile source) {
        return recoveryTracker.startAttempt(source, SourceOperationType.PROCESS);
    }

    public SourceLeaseKeeper.Lease keepRecoveryLease(WorkspaceFile source, RecoveryClaim claim) {
        return leaseKeeper == null ? () -> { }
                : leaseKeeper.keep(source, SourceOperationType.PROCESS, claim);
    }

    public void failRecoveryAttempt(WorkspaceFile source, RecoveryClaim claim, Exception exception) {
        recoveryTracker.fail(source, SourceOperationType.PROCESS, claim, exception);
    }

    public void assignRecoveryJob(WorkspaceFile source, String jobId, RecoveryClaim claim) {
        recoveryTracker.assignJob(source, jobId, claim);
    }

    public <T> T processClaimedRecovery(IngestionJobDetails job, IngestionContentType contentType,
            WorkspaceFile source, RecoveryClaim claim, SourceProcessingOperation<T> operation) throws Exception {
        return process(job, contentType, source, operation, claim);
    }

    private <T> T process(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            SourceProcessingOperation<T> operation, RecoveryClaim existingClaim) throws Exception {
        boolean sourceTransitioned = false;
        long startedAt = System.nanoTime();
        RecoveryClaim claim = existingClaim;
        try {
            if (claim == null) {
                claim = recoveryTracker.startAttempt(source, SourceOperationType.PROCESS);
            }
            if (!ingestionJobService.markStepRunning(job.jobId(), contentType)) {
                throw new IllegalStateException("Ingestion source is no longer pending");
            }
            workspaceFileService.markProcessing(job.workspaceId(), source.id());
            sourceTransitioned = true;
            T result;
            if (leaseKeeper == null) {
                result = operation.process();
            } else {
                try (AutoCloseable ignored = leaseKeeper.keep(source, SourceOperationType.PROCESS, claim)) {
                    result = operation.process();
                }
            }
            completionService.complete(job, contentType, source, claim,
                    result instanceof SourceProcessingResult processed ? processed.stagedIndex() : null);
            telemetry.recordAttempt(SourceOperationType.PROCESS, "success", System.nanoTime() - startedAt);
            return result;
        } catch (Exception exception) {
            telemetry.recordAttempt(SourceOperationType.PROCESS, "failure", System.nanoTime() - startedAt);
            if (claim != null) {
                try {
                    completionService.fail(job, contentType, source, claim, sourceTransitioned, exception);
                } catch (RuntimeException completionException) {
                    exception.addSuppressed(completionException);
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
