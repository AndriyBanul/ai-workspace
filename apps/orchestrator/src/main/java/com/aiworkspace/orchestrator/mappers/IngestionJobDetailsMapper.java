package com.aiworkspace.orchestrator.mappers;

import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionJobStepDetails;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IngestionJobDetailsMapper {

    @Mapping(target = "jobId", source = "job.id")
    IngestionJobDetails toDetails(IngestionJob job, List<IngestionJobStepDetails> steps);

    @Mapping(target = "type", expression = "java(step.contentType().apiName())")
    IngestionJobStepDetails toDetails(IngestionJobStep step);
}
