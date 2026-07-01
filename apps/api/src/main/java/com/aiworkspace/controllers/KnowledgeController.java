package com.aiworkspace.controllers;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.services.KnowledgeService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<WorkspaceKnowledge> getWorkspaceKnowledge(@PathVariable String workspaceId) {
        try {
            return knowledgeService.findWorkspaceKnowledge(workspaceId)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Workspace knowledge was not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            log.warn("Failed to fetch knowledge for workspace '{}'", workspaceId, exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch workspace knowledge", exception);
        }
    }
}
