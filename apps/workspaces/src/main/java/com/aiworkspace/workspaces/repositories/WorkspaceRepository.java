package com.aiworkspace.workspaces.repositories;

import com.aiworkspace.workspaces.entities.WorkspaceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {

    List<WorkspaceEntity> findAllByOwnerId(String ownerId);

    Optional<WorkspaceEntity> findByIdAndOwnerId(String id, String ownerId);
}
