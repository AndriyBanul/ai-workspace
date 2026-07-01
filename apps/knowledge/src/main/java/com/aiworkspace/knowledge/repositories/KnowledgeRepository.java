package com.aiworkspace.knowledge.repositories;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import java.io.IOException;
import java.util.Optional;

public interface KnowledgeRepository {

    Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException;

    void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException;
}
