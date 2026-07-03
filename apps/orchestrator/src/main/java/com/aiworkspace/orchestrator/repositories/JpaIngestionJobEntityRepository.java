package com.aiworkspace.orchestrator.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaIngestionJobEntityRepository extends JpaRepository<IngestionJobEntity, String> {
}
