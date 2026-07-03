package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaIngestionJobStepEntityRepository extends JpaRepository<IngestionJobStepEntity, String> {

    List<IngestionJobStepEntity> findByJobId(String jobId);

    Optional<IngestionJobStepEntity> findByJobIdAndContentType(String jobId, IngestionContentType contentType);
}
