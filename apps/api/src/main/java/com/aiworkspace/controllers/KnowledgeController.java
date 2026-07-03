package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public KnowledgeController(
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<WorkspaceKnowledge> getWorkspaceKnowledge(
            @PathVariable String workspaceId,
            Authentication authentication
    )
            throws IOException {
        workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        return knowledgeService.findWorkspaceKnowledge(workspaceId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Workspace knowledge was not found"));
    }

    @PostMapping("/workspaces/{workspaceId}/answers")
    public ResponseEntity<WorkspaceKnowledgeAnswer> answerWorkspaceQuestion(
            @PathVariable String workspaceId,
            @RequestBody WorkspaceQuestionRequest request,
            Authentication authentication
    ) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        return ResponseEntity.ok(knowledgeService.answerWorkspaceQuestion(workspaceId, request.question()));
    }

    public record WorkspaceQuestionRequest(String question) {
    }
}
