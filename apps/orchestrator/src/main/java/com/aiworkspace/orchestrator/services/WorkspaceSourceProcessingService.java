package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Dispatches source-specific extraction and indexes the normalized result. */
@Service
public class WorkspaceSourceProcessingService {

    private final DocumentService documentService;
    private final AudioService audioService;
    private final ImageService imageService;
    private final VideoService videoService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;

    public WorkspaceSourceProcessingService(DocumentService documentService, AudioService audioService,
            ImageService imageService, VideoService videoService, WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService) {
        this.documentService = documentService;
        this.audioService = audioService;
        this.imageService = imageService;
        this.videoService = videoService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
    }

    public Object process(IngestionJobDetails job, WorkspaceFile source, OrchestrationContent original)
            throws Exception {
        return switch (source.sourceType()) {
            case DOCUMENT -> processDocument(job, source, original);
            case AUDIO -> processAudio(job, source, original);
            case IMAGE -> processImage(job, source, original);
            case VIDEO -> processVideo(job, source, original);
            case WEB_PAGE -> processWebPage(job, source);
            case YOUTUBE -> processYouTube(job, source);
        };
    }

    private TextDocumentUploadResponse processDocument(IngestionJobDetails job, WorkspaceFile source,
            OrchestrationContent original) throws Exception {
        byte[] bytes = readContent(job.workspaceId(), source.id());
        ParsedTextDocument document = documentService.extractDocumentText(
                original.filename(), original.contentType(), bytes);
        knowledgeService.recordDocumentsInfo(
                job.workspaceId(), original.filename(), job.jobId(), documentService.chunkForKnowledge(document),
                new KnowledgeSourceMetadata(source.id(), null, document.extractedAt(), document.parserVersion()));
        return new TextDocumentUploadResponse(
                source.id(), document.filename(), document.detectedContentType(), document.title(), source.sizeBytes(),
                document.content().length(), document.blocks().size(), document.extractedAt(), document.parserVersion());
    }

    private AudioTranscriptionResponse processAudio(IngestionJobDetails job, WorkspaceFile source,
            OrchestrationContent original) throws Exception {
        AudioTranscription transcription = audioService.transcribe(
                original.filename(), readContent(job.workspaceId(), source.id()));
        knowledgeService.recordAudioInfo(
                job.workspaceId(), original.filename(), job.jobId(), audioService.chunksForKnowledge(transcription),
                audioService.knowledgeSourceMetadata(source.id()));
        return new AudioTranscriptionResponse(
                transcription.filename(), source.sizeBytes(), transcription.language(), transcription.text(),
                transcription.segments());
    }

    private ImageDescriptionResponse processImage(IngestionJobDetails job, WorkspaceFile source,
            OrchestrationContent original) throws Exception {
        ImageDescription description = imageService.describe(
                original.filename(), original.contentType(), readContent(job.workspaceId(), source.id()));
        knowledgeService.recordImagesInfo(
                job.workspaceId(), original.filename(), job.jobId(), description.description(),
                new KnowledgeSourceMetadata(source.id(), null, Instant.now(), "ai-workspace-image-description-v1"));
        return new ImageDescriptionResponse(
                description.filename(), source.sizeBytes(), description.mimeType(), description.description());
    }

    private VideoDescriptionResponse processVideo(IngestionJobDetails job, WorkspaceFile source,
            OrchestrationContent original) throws Exception {
        VideoDescription description = videoService.describe(
                original.filename(), original.contentType(), readContent(job.workspaceId(), source.id()));
        knowledgeService.recordVideoInfo(
                job.workspaceId(), original.filename(), job.jobId(), videoService.knowledgeText(description),
                new KnowledgeSourceMetadata(source.id(), null, Instant.now(), "ai-workspace-video-analysis-v1"));
        return new VideoDescriptionResponse(
                description.filename(), source.sizeBytes(), description.mimeType(), description.description(),
                description.transcript(), description.language(), description.segments());
    }

    private WebPageExtractResponse processWebPage(IngestionJobDetails job, WorkspaceFile source) throws IOException {
        ExtractedWebPage page = documentService.extractWebPage(source.sourceUrl());
        knowledgeService.recordDocumentsInfo(
                job.workspaceId(), page.title(), job.jobId(), documentService.chunkTextForKnowledge(page.content()),
                new KnowledgeSourceMetadata(source.id(), page.url(), page.extractedAt(), page.parserVersion()));
        int characterCount = page.content().length();
        return new WebPageExtractResponse(
                source.id(), page.url(), page.contentType(), page.title(), characterCount, characterCount,
                characterCount, false, page.extractedAt(), page.parserVersion());
    }

    private YouTubeVideoIngestionResponse processYouTube(IngestionJobDetails job, WorkspaceFile source)
            throws IOException, InterruptedException {
        VideoDescription description = videoService.describeYouTube(source.sourceUrl());
        String canonicalUrl = videoService.canonicalYouTubeUrl(source.sourceUrl());
        knowledgeService.recordVideoInfo(
                job.workspaceId(), description.filename(), job.jobId(), videoService.knowledgeText(description),
                new KnowledgeSourceMetadata(
                        source.id(), canonicalUrl, Instant.now(), "ai-workspace-youtube-video-analysis-v1"));
        return new YouTubeVideoIngestionResponse(
                source.id(), videoService.youtubeVideoId(canonicalUrl), canonicalUrl, description.description(),
                description.transcript(), description.language(), description.segments());
    }

    private byte[] readContent(String workspaceId, String sourceId) throws IOException {
        try (InputStream input = workspaceFileService.readContent(workspaceId, sourceId)) {
            return input.readAllBytes();
        }
    }
}
