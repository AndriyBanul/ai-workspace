package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.SourceOperationType;
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

    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService) {
        this(workspaceService, knowledgeService, null, SourceRecoveryTracker.noop());
    }

    @Autowired
    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService,
            WorkspaceFileService workspaceFileService, SourceRecoveryTracker recoveryTracker) {
        this.workspaceService = workspaceService;
        this.knowledgeService = knowledgeService;
        this.workspaceFileService = workspaceFileService;
        this.recoveryTracker = recoveryTracker;
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
        boolean recoveryStarted = false;
        try {
            recoveryTracker.startAttempt(file, SourceOperationType.DELETE);
            recoveryStarted = true;
            knowledgeService.deleteSourceKnowledge(file.workspaceId(), file.id());
            operation.delete();
            recoveryTracker.complete(file, SourceOperationType.DELETE);
        } catch (IOException | RuntimeException exception) {
            if (recoveryStarted) {
                try {
                    recoveryTracker.fail(file, SourceOperationType.DELETE, exception);
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
