package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.services.WorkspaceService;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceLifecycleService {

    private final WorkspaceService workspaceService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceFileService workspaceFileService;
    private final SourceRecoveryTracker recoveryTracker;
    private final SourceLeaseKeeper leaseKeeper;

    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService) {
        this(workspaceService, knowledgeService, null, SourceRecoveryTracker.noop());
    }

    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker) {
        this(workspaceService, knowledgeService, workspaceFileService, recoveryTracker, null);
    }

    @Autowired
    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker,
            SourceLeaseKeeper leaseKeeper) {
        this.workspaceService = workspaceService;
        this.knowledgeService = knowledgeService;
        this.workspaceFileService = workspaceFileService;
        this.recoveryTracker = recoveryTracker;
        this.leaseKeeper = leaseKeeper;
    }

    public void deleteFile(String ownerId, String workspaceId, String fileId) throws IOException {
        WorkspaceFile file = workspaceService.getFile(ownerId, workspaceId, fileId);
        if (file.status() == WorkspaceFileStatus.PROCESSING) {
            throw new WorkspaceSourceConflictException("Workspace source cannot be deleted while processing");
        }
        deleteSource(file, () -> workspaceService.deleteFile(ownerId, workspaceId, fileId));
    }

    public void retryDeleteSource(String workspaceId, String sourceId) throws IOException {
        WorkspaceFile file = workspaceFileService.getFile(workspaceId, sourceId);
        deleteSource(file, () -> workspaceFileService.deleteFile(workspaceId, sourceId));
    }

    public void deleteWorkspace(String ownerId, String workspaceId) throws IOException {
        String ownedWorkspaceId = workspaceService.getWorkspace(ownerId, workspaceId).id();
        knowledgeService.deleteWorkspaceKnowledge(ownedWorkspaceId);
        workspaceService.deleteWorkspace(ownerId, ownedWorkspaceId);
    }

    private void deleteSource(WorkspaceFile file, DeleteOperation operation) throws IOException {
        RecoveryClaim claim = null;
        try {
            claim = recoveryTracker.startAttempt(file, SourceOperationType.DELETE);
            try (SourceLeaseKeeper.Lease ignored = leaseKeeper == null ? () -> { }
                    : leaseKeeper.keep(file, SourceOperationType.DELETE, claim)) {
                knowledgeService.deleteSourceKnowledge(file.workspaceId(), file.id());
                recoveryTracker.assertCurrent(file, SourceOperationType.DELETE, claim);
                operation.delete();
            }
            recoveryTracker.complete(file, SourceOperationType.DELETE, claim);
        } catch (IOException | RuntimeException exception) {
            if (claim != null) {
                try {
                    recoveryTracker.fail(file, SourceOperationType.DELETE, claim, exception);
                } catch (RuntimeException recoveryException) {
                    exception.addSuppressed(recoveryException);
                }
            }
            throw exception;
        }
    }

    @FunctionalInterface
    private interface DeleteOperation {
        void delete() throws IOException;
    }
}
