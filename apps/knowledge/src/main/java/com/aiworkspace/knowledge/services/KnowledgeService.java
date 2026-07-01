package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    public Optional<WorkspaceKnowledge> findWorkspaceKnowledge(String workspaceId) throws IOException {
        return knowledgeRepository.findByWorkspaceId(normalizedWorkspaceId(workspaceId));
    }

    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        if (field == null) {
            throw new IllegalArgumentException("Knowledge field must not be null");
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge field value must not be blank");
        }

        knowledgeRepository.updateWorkspaceKnowledgeField(normalizedWorkspaceId(workspaceId), field, value.trim());
    }

    private String normalizedWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }

        return workspaceId.trim();
    }
}
