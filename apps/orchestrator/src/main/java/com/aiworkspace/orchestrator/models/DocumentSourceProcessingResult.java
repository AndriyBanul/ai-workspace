package com.aiworkspace.orchestrator.models;

import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;

public record DocumentSourceProcessingResult(TextDocumentUploadResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
