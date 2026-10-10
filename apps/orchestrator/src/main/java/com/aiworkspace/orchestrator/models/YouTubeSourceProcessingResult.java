package com.aiworkspace.orchestrator.models;

import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;

public record YouTubeSourceProcessingResult(YouTubeVideoIngestionResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
