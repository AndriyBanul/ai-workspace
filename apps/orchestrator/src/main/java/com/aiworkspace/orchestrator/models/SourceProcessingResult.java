package com.aiworkspace.orchestrator.models;

import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;

/** A media-specific response and the index generation staged for publication. */
public sealed interface SourceProcessingResult permits DocumentSourceProcessingResult,
        AudioSourceProcessingResult, ImageSourceProcessingResult, VideoSourceProcessingResult,
        WebPageSourceProcessingResult, YouTubeSourceProcessingResult {

    StagedKnowledgeIndex stagedIndex();
}
