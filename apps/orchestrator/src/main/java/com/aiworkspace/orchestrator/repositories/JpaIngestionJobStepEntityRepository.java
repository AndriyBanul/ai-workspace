package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.IngestionJobStepEntity;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
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
interface JpaIngestionJobStepEntityRepository extends JpaRepository<IngestionJobStepEntity, String> {

    List<IngestionJobStepEntity> findByJobId(String jobId);

    Optional<IngestionJobStepEntity> findByJobIdAndContentType(String jobId, IngestionContentType contentType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJobStepEntity step
            set step.status = :status,
                step.startedAt = :startedAt,
                step.completedAt = null,
                step.errorMessage = null
            where step.jobId = :jobId
              and step.contentType = :contentType
              and step.status in :activeStatuses
            """)
    int markRunningIfActive(
            @Param("jobId") String jobId,
            @Param("contentType") IngestionContentType contentType,
            @Param("status") IngestionStepStatus status,
            @Param("startedAt") Instant startedAt,
            @Param("activeStatuses") Collection<IngestionStepStatus> activeStatuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJobStepEntity step
            set step.status = :status,
                step.completedAt = :completedAt,
                step.errorMessage = null
            where step.jobId = :jobId
              and step.contentType = :contentType
              and step.status in :activeStatuses
            """)
    int markCompletedIfActive(
            @Param("jobId") String jobId,
            @Param("contentType") IngestionContentType contentType,
            @Param("status") IngestionStepStatus status,
            @Param("completedAt") Instant completedAt,
            @Param("activeStatuses") Collection<IngestionStepStatus> activeStatuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IngestionJobStepEntity step
            set step.status = :status,
                step.completedAt = :completedAt,
                step.errorMessage = :errorMessage
            where step.jobId = :jobId
              and step.contentType = :contentType
              and step.status in :activeStatuses
            """)
    int markFailedIfActive(
            @Param("jobId") String jobId,
            @Param("contentType") IngestionContentType contentType,
            @Param("status") IngestionStepStatus status,
            @Param("completedAt") Instant completedAt,
            @Param("errorMessage") String errorMessage,
            @Param("activeStatuses") Collection<IngestionStepStatus> activeStatuses
    );
}
