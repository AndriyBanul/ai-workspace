package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.entities.IngestionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaIngestionJobEntityRepository extends JpaRepository<IngestionJobEntity, String> {
}
