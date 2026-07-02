package com.aiworkspace.knowledge.repositories;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {

    Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException;

    List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException;

    List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) throws IOException;

    void addKnowledgeItem(KnowledgeItem item) throws IOException;

    void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException;
}
