package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.OrchestrationSubmissionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

@Repository
public interface OrchestrationSubmissionRequestRepository
        extends JpaRepository<OrchestrationSubmissionRequestEntity, String> {

    @Modifying
    @Query("delete from OrchestrationSubmissionRequestEntity request where request.createdAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
