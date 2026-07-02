package com.aiworkspace.orchestrator.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaIngestionJobEntityRepository extends JpaRepository<IngestionJobEntity, String> {
}
