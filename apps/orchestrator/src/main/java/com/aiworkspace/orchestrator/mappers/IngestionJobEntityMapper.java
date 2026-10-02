package com.aiworkspace.orchestrator.mappers;

import com.aiworkspace.orchestrator.entities.IngestionJobEntity;
import com.aiworkspace.orchestrator.entities.IngestionJobStepEntity;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IngestionJobEntityMapper {

    IngestionJobEntity toEntity(IngestionJob job);

    IngestionJobStepEntity toEntity(IngestionJobStep step);

    IngestionJob toModel(IngestionJobEntity entity);

    IngestionJobStep toModel(IngestionJobStepEntity entity);
}
