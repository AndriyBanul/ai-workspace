package com.aiworkspace.controllers;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.users.services.UserAccountService;
import java.io.IOException;
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
    private final UserAccountService userAccountService;

    public KnowledgeController(KnowledgeService knowledgeService, UserAccountService userAccountService) {
        this.knowledgeService = knowledgeService;
        this.userAccountService = userAccountService;
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<WorkspaceKnowledge> getWorkspaceKnowledge(
            @PathVariable String workspaceId,
            Authentication authentication
    )
            throws IOException {
        return ResponseEntity.ok(knowledgeService.getWorkspaceKnowledge(
                userAccountService.currentUserId(authentication),
                workspaceId
        ));
    }

    @PostMapping("/workspaces/{workspaceId}/answers")
    public ResponseEntity<WorkspaceKnowledgeAnswer> answerWorkspaceQuestion(
            @PathVariable String workspaceId,
            @RequestBody WorkspaceQuestionRequest request,
            Authentication authentication
    ) throws IOException {
        return ResponseEntity.ok(
                knowledgeService.answerWorkspaceQuestion(
                userAccountService.currentUserId(authentication),
                workspaceId,
                request
        ));
    }
}
