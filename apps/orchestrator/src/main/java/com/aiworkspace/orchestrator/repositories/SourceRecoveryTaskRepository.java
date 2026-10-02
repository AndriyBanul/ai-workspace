package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.SourceRecoveryTaskEntity;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceRecoveryTaskRepository extends JpaRepository<SourceRecoveryTaskEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from SourceRecoveryTaskEntity task where task.sourceId = :sourceId")
    Optional<SourceRecoveryTaskEntity> findForUpdate(@Param("sourceId") String sourceId);

    @Query("""
            select task from SourceRecoveryTaskEntity task
            where (task.status = :scheduled and task.nextAttemptAt <= :now)
               or (task.status = :running and task.leaseExpiresAt <= :now)
               or (task.status = :completed and task.nextAttemptAt <= :now)
            order by task.nextAttemptAt asc, task.updatedAt asc
            """)
    List<SourceRecoveryTaskEntity> findDueTasks(
            @Param("scheduled") SourceRecoveryStatus scheduled,
            @Param("running") SourceRecoveryStatus running,
            @Param("completed") SourceRecoveryStatus completed,
            @Param("now") Instant now,
            Pageable pageable
    );
}
