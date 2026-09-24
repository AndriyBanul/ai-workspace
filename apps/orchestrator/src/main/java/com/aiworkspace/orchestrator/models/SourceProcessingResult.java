package com.aiworkspace.orchestrator.models;

import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;

/** A media-specific response and the index generation staged for publication. */
public sealed interface SourceProcessingResult permits SourceProcessingResult.Document,
        SourceProcessingResult.Audio, SourceProcessingResult.Image, SourceProcessingResult.Video,
        SourceProcessingResult.WebPage, SourceProcessingResult.YouTube {

    StagedKnowledgeIndex stagedIndex();

    record Document(TextDocumentUploadResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }

    record Audio(AudioTranscriptionResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }

    record Image(ImageDescriptionResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }

    record Video(VideoDescriptionResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }

    record WebPage(WebPageExtractResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }

    record YouTube(YouTubeVideoIngestionResponse response, StagedKnowledgeIndex stagedIndex)
            implements SourceProcessingResult {
    }
}
