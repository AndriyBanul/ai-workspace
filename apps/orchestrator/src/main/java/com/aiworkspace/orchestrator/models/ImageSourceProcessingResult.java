package com.aiworkspace.orchestrator.models;

import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;

public record ImageSourceProcessingResult(ImageDescriptionResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
