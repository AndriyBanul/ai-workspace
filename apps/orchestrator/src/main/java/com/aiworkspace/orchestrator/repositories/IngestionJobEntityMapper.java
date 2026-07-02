package com.aiworkspace.orchestrator.repositories;

import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface IngestionJobEntityMapper {

    IngestionJobEntity toEntity(IngestionJob job);

    IngestionJobStepEntity toEntity(IngestionJobStep step);

    IngestionJob toModel(IngestionJobEntity entity);

    IngestionJobStep toModel(IngestionJobStepEntity entity);
}
