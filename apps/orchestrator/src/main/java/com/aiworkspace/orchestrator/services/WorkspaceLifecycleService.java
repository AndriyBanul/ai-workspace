package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceLifecycleService {

    private final WorkspaceService workspaceService;
    private final KnowledgeService knowledgeService;

    public WorkspaceLifecycleService(WorkspaceService workspaceService, KnowledgeService knowledgeService) {
        this.workspaceService = workspaceService;
        this.knowledgeService = knowledgeService;
    }

    public void deleteFile(String ownerId, String workspaceId, String fileId) throws IOException {
        WorkspaceFile file = workspaceService.getFile(ownerId, workspaceId, fileId);
        if (file.status() == WorkspaceFileStatus.PROCESSING) {
            throw new WorkspaceSourceConflictException("Workspace source cannot be deleted while processing");
        }
        knowledgeService.deleteSourceKnowledge(file.workspaceId(), file.id());
        workspaceService.deleteFile(ownerId, workspaceId, fileId);
    }

    public void deleteWorkspace(String ownerId, String workspaceId) throws IOException {
        String ownedWorkspaceId = workspaceService.getWorkspace(ownerId, workspaceId).id();
        knowledgeService.deleteWorkspaceKnowledge(ownedWorkspaceId);
        workspaceService.deleteWorkspace(ownerId, ownedWorkspaceId);
    }
}
