package com.aiworkspace.controllers;

import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.users.services.UserAccountService;
import com.aiworkspace.workspaces.models.CreateWorkspaceRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceFileService workspaceFileService;
    private final UserAccountService userAccountService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            WorkspaceFileService workspaceFileService,
            UserAccountService userAccountService
    ) {
        this.workspaceService = workspaceService;
        this.workspaceFileService = workspaceFileService;
        this.userAccountService = userAccountService;
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return ResponseEntity.status(CREATED).body(workspaceService.createWorkspace(
                userAccountService.currentUserId(authentication),
                request.name()
        ));
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> listWorkspaces(Authentication authentication) {
        return ResponseEntity.ok(workspaceService.listWorkspaces(userAccountService.currentUserId(authentication)));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<Workspace> getWorkspace(@PathVariable String workspaceId, Authentication authentication) {
        return ResponseEntity.ok(workspaceService.getWorkspace(
                userAccountService.currentUserId(authentication),
                workspaceId
        ));
    }

    @GetMapping("/{workspaceId}/files")
    public ResponseEntity<List<WorkspaceFile>> listFiles(
            @PathVariable String workspaceId,
            Authentication authentication
    ) {
        Workspace workspace = workspaceService.getWorkspace(userAccountService.currentUserId(authentication), workspaceId);
        return ResponseEntity.ok(workspaceFileService.listFiles(workspace.id()));
    }

    @GetMapping("/{workspaceId}/files/{fileId}")
    public ResponseEntity<WorkspaceFile> getFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) {
        Workspace workspace = workspaceService.getWorkspace(userAccountService.currentUserId(authentication), workspaceId);
        return ResponseEntity.ok(workspaceFileService.getFile(workspace.id(), fileId));
    }

    @DeleteMapping("/{workspaceId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) throws IOException {
        Workspace workspace = workspaceService.getWorkspace(userAccountService.currentUserId(authentication), workspaceId);
        workspaceFileService.deleteFile(workspace.id(), fileId);
        return ResponseEntity.noContent().build();
    }
}
