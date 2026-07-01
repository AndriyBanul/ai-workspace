package com.aiworkspace.knowledge.repositories;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import java.io.IOException;
import java.util.Optional;

public interface KnowledgeRepository {

    Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) throws IOException;
}
