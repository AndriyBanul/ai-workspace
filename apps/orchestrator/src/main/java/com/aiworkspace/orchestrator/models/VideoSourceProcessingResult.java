package com.aiworkspace.orchestrator.models;

import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.videos.models.VideoDescriptionResponse;

public record VideoSourceProcessingResult(VideoDescriptionResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
