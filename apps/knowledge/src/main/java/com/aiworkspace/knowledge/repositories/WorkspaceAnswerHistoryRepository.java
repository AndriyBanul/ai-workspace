package com.aiworkspace.knowledge.repositories;

import com.aiworkspace.knowledge.entities.WorkspaceAnswerHistoryEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceAnswerHistoryRepository extends JpaRepository<WorkspaceAnswerHistoryEntity, String> {

    List<WorkspaceAnswerHistoryEntity> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable page);
}
