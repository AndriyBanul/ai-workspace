package com.aiworkspace.controllers;

import com.aiworkspace.users.services.UserAccountService;
import com.aiworkspace.orchestrator.services.WorkspaceLifecycleService;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.workspaces.models.CreateWorkspaceRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
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
import static org.springframework.http.HttpStatus.ACCEPTED;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserAccountService userAccountService;
    private final WorkspaceLifecycleService workspaceLifecycleService;
    private final OrchestratorService orchestratorService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            UserAccountService userAccountService,
            WorkspaceLifecycleService workspaceLifecycleService,
            OrchestratorService orchestratorService
    ) {
        this.workspaceService = workspaceService;
        this.userAccountService = userAccountService;
        this.workspaceLifecycleService = workspaceLifecycleService;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request, Authentication authentication) {
        return ResponseEntity.status(CREATED).body(workspaceService.createWorkspace(
                userAccountService.currentUserId(authentication),
                request
        ));
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> listWorkspaces(Authentication authentication) {
        return ResponseEntity.ok(workspaceService.listWorkspaces(userAccountService.currentUserId(authentication)));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<Workspace> getWorkspace(@PathVariable String workspaceId, Authentication authentication) {
        return ResponseEntity.ok(workspaceService.getWorkspace(userAccountService.currentUserId(authentication), workspaceId));
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable String workspaceId,
            Authentication authentication
    ) throws IOException {
        workspaceLifecycleService.deleteWorkspace(
                userAccountService.currentUserId(authentication),
                workspaceId
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/{workspaceId}/files", "/{workspaceId}/sources"})
    public ResponseEntity<List<WorkspaceFile>> listFiles(
            @PathVariable String workspaceId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workspaceService.listFiles(userAccountService.currentUserId(authentication), workspaceId));
    }

    @GetMapping({"/{workspaceId}/files/{fileId}", "/{workspaceId}/sources/{fileId}"})
    public ResponseEntity<WorkspaceFile> getFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workspaceService.getFile(userAccountService.currentUserId(authentication), workspaceId, fileId));
    }

    @DeleteMapping({"/{workspaceId}/files/{fileId}", "/{workspaceId}/sources/{fileId}"})
    public ResponseEntity<Void> deleteFile(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) throws IOException {
        workspaceLifecycleService.deleteFile(
                userAccountService.currentUserId(authentication),
                workspaceId,
                fileId
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/{workspaceId}/files/{fileId}/reprocess", "/{workspaceId}/sources/{fileId}/reprocess"})
    public ResponseEntity<OrchestrationSubmission> reprocessSource(
            @PathVariable String workspaceId,
            @PathVariable String fileId,
            Authentication authentication
    ) throws IOException {
        return ResponseEntity.status(ACCEPTED).body(orchestratorService.reprocessSource(
                userAccountService.currentUserId(authentication), workspaceId, fileId));
    }
}
