package com.aiworkspace.orchestrator.models;

import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;

public record WebPageSourceProcessingResult(WebPageExtractResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
