package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.services.WorkspaceFileService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/files")
public class WorkspaceFileController {

    private final WorkspaceFileService workspaceFileService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public WorkspaceFileController(
            WorkspaceFileService workspaceFileService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.workspaceFileService = workspaceFileService;
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceFile>> listFiles(
            @PathVariable String workspaceId,
            Authentication authentication
    ) {
        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        return ResponseEntity.ok(workspaceFileService.listFiles(workspace.id()));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<WorkspaceFile> getFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) {
        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        return ResponseEntity.ok(workspaceFileService.getFile(workspace.id(), fileId));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) throws IOException {
        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        workspaceFileService.deleteFile(workspace.id(), fileId);
        return ResponseEntity.noContent().build();
    }
}
