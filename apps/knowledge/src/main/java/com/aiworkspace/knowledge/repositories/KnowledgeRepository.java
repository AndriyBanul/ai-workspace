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

    default List<KnowledgeItem> searchKnowledgeItems(
            String workspaceId,
            String query,
            List<Float> queryEmbedding,
            int limit,
            int candidateLimit,
            int rrfRankConstant
    ) throws IOException {
        return searchKnowledgeItems(workspaceId, query, limit);
    }

    default List<KnowledgeItem> searchKnowledgeItemsByVector(
            String workspaceId,
            List<Float> queryEmbedding,
            int limit,
            int candidateLimit
    ) throws IOException {
        return searchKnowledgeItems(workspaceId, "", limit);
    }

    default List<KnowledgeItem> expandNeighbors(String workspaceId, List<KnowledgeItem> matches) throws IOException {
        return matches;
    }

    void addKnowledgeItem(KnowledgeItem item) throws IOException;

    default void addKnowledgeItems(List<KnowledgeItem> items) throws IOException {
        for (KnowledgeItem item : items) {
            addKnowledgeItem(item);
        }
    }

    default void replaceKnowledgeItems(String workspaceId, String sourceId, List<KnowledgeItem> items)
            throws IOException {
        deleteKnowledgeItemsBySourceId(workspaceId, sourceId);
        addKnowledgeItems(items);
    }

    default void stageKnowledgeItems(String workspaceId, String sourceId, String generation,
            List<KnowledgeItem> items) throws IOException {
        addKnowledgeItems(items);
    }

    default int countSourceGeneration(String workspaceId, String sourceId, String generation) throws IOException {
        return -1;
    }

    default void pruneSourceGenerations(String workspaceId, String sourceId, String activeGeneration)
            throws IOException {
    }

    void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) throws IOException;

    void deleteKnowledgeItemsByWorkspaceId(String workspaceId) throws IOException;

    void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException;
}
