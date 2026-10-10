package com.aiworkspace.orchestrator.models;

import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;

public record AudioSourceProcessingResult(AudioTranscriptionResponse response, StagedKnowledgeIndex stagedIndex)
        implements SourceProcessingResult {
}
