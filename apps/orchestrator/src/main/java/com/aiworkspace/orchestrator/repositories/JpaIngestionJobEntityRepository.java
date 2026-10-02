package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.IngestionJobEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
interface JpaIngestionJobEntityRepository extends JpaRepository<IngestionJobEntity, String> {

    List<IngestionJobEntity> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from IngestionJobEntity job where job.id = :id")
    Optional<IngestionJobEntity> lockById(@Param("id") String id);
}
