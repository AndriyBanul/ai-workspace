package com.aiworkspace.controllers;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceAnswerHistoryEntry;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.knowledge.services.WorkspaceAnswerHistoryService;
import com.aiworkspace.users.services.UserAccountService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final WorkspaceAnswerHistoryService answerHistory;
    private final UserAccountService userAccountService;

    public KnowledgeController(KnowledgeService knowledgeService, WorkspaceAnswerHistoryService answerHistory,
            UserAccountService userAccountService) {
        this.knowledgeService = knowledgeService;
        this.answerHistory = answerHistory;
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
                answerHistory.ask(
                userAccountService.currentUserId(authentication),
                workspaceId,
                request
        ));
    }

    @GetMapping("/workspaces/{workspaceId}/answers")
    public ResponseEntity<List<WorkspaceAnswerHistoryEntry>> listAnswers(@PathVariable String workspaceId,
            @RequestParam(defaultValue = "50") int limit, Authentication authentication) throws IOException {
        return ResponseEntity.ok(answerHistory.list(
                userAccountService.currentUserId(authentication), workspaceId, limit));
    }
}
