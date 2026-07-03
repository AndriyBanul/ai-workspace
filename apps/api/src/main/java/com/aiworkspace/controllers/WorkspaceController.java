package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final CurrentUserService currentUserService;

    public WorkspaceController(WorkspaceService workspaceService, CurrentUserService currentUserService) {
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return ResponseEntity.status(CREATED).body(workspaceService.createWorkspace(
                currentUserService.currentUserId(authentication),
                request.name()
        ));
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> listWorkspaces(Authentication authentication) {
        return ResponseEntity.ok(workspaceService.listWorkspaces(currentUserService.currentUserId(authentication)));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<Workspace> getWorkspace(@PathVariable String workspaceId, Authentication authentication) {
        return ResponseEntity.ok(workspaceService.getWorkspace(
                currentUserService.currentUserId(authentication),
                workspaceId
        ));
    }

    public record CreateWorkspaceRequest(String name) {
    }
}
