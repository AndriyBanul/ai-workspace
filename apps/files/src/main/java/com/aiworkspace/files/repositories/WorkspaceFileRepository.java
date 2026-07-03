package com.aiworkspace.files.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFileEntity, String> {

    List<WorkspaceFileEntity> findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(String workspaceId);

    Optional<WorkspaceFileEntity> findByIdAndWorkspaceIdAndDeletedAtIsNull(String id, String workspaceId);
}
