package com.aiworkspace.workspaces.repositories;

import com.aiworkspace.workspaces.entities.WorkspaceFileEntity;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFileEntity, String> {

    List<WorkspaceFileEntity> findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(String workspaceId);

    Optional<WorkspaceFileEntity> findByIdAndWorkspaceIdAndDeletedAtIsNull(String id, String workspaceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkspaceFileEntity file
            set file.status = :status,
                file.updatedAt = :updatedAt
            where file.id = :id
              and file.workspaceId = :workspaceId
              and file.deletedAt is null
              and file.status in :activeStatuses
            """)
    int updateStatusIfActive(
            @Param("id") String id,
            @Param("workspaceId") String workspaceId,
            @Param("status") WorkspaceFileStatus status,
            @Param("updatedAt") Instant updatedAt,
            @Param("activeStatuses") Collection<WorkspaceFileStatus> activeStatuses
    );
}
