package com.aiworkspace.videos.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoAnalysis;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;
import com.aiworkspace.videos.interfaces.VideoGenerationProvider;
import com.aiworkspace.videos.interfaces.VideoUnderstandingProvider;
import com.aiworkspace.videos.interfaces.YouTubeVideoUnderstandingProvider;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

    private final VideoUnderstandingProvider videoUnderstandingProvider;
    private final YouTubeVideoUnderstandingProvider youTubeVideoUnderstandingProvider;
    private final VideoGenerationProvider videoGenerationProvider;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final String descriptionPrompt;
    private final VideoValidator videoValidator;

    public VideoService(
            VideoUnderstandingProvider videoUnderstandingProvider,
            VideoGenerationProvider videoGenerationProvider,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt
    ) {
        this(
                videoUnderstandingProvider,
                videoUnderstandingProvider instanceof YouTubeVideoUnderstandingProvider provider ? provider : null,
                videoGenerationProvider,
                null,
                null,
                null,
                descriptionPrompt,
                new VideoValidator()
        );
    }

    @Autowired
    public VideoService(
            VideoUnderstandingProvider videoUnderstandingProvider,
            YouTubeVideoUnderstandingProvider youTubeVideoUnderstandingProvider,
            VideoGenerationProvider videoGenerationProvider,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt,
            VideoValidator videoValidator
    ) {
        this.videoUnderstandingProvider = videoUnderstandingProvider;
        this.youTubeVideoUnderstandingProvider = youTubeVideoUnderstandingProvider;
        this.videoGenerationProvider = videoGenerationProvider;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.descriptionPrompt = descriptionPrompt;
        this.videoValidator = videoValidator;
    }

    public VideoDescription describe(String filename, String contentType, byte[] videoContent)
            throws IOException, InterruptedException {
        videoValidator.validateVideo(filename, videoContent);

        String mimeType = videoValidator.mimeType(filename, contentType);
        VideoAnalysis analysis = videoUnderstandingProvider.analyze(videoContent, mimeType, descriptionPrompt);

        return new VideoDescription(
                filename,
                mimeType,
                analysis.summary(),
                analysis.transcript(),
                analysis.language(),
                analysis.segments()
        );
    }

    public GeneratedVideo generate(String description) throws IOException, InterruptedException {
        videoValidator.validateGenerationDescription(description);

        return videoGenerationProvider.generate(description.trim());
    }

    public VideoDescriptionResponse describeWorkspaceVideo(
            String ownerId,
            String workspaceId,
            String filename,
            String contentType,
            byte[] content
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(ownerId, workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.VIDEO)
                .originalFilename(filename)
                .contentType(contentType)
                .content(new ByteArrayInputStream(content))
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        VideoDescription description;
        try {
            description = describe(filename, contentType, content);
            knowledgeService.recordVideoInfo(workspace.id(), description.filename(), null, knowledgeText(description));
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        return new VideoDescriptionResponse(
                description.filename(),
                content.length,
                description.mimeType(),
                description.description(),
                description.transcript(),
                description.language(),
                description.segments()
        );
    }

    public GeneratedVideo generate(VideoGenerationRequest request) throws IOException, InterruptedException {
        videoValidator.validateGenerationRequest(request);

        return generate(request.description());
    }

    public YouTubeVideoIngestionResponse ingestYouTube(
            String ownerId,
            YouTubeVideoIngestionRequest request
    ) throws IOException, InterruptedException {
        videoValidator.validateYouTubeIngestionRequest(request);
        if (youTubeVideoUnderstandingProvider == null) {
            throw new IllegalStateException("YouTube video understanding provider is not configured");
        }

        Workspace workspace = workspaceService.getWorkspace(ownerId, request.workspaceId());
        String videoId = videoValidator.youtubeVideoId(request.url());
        String url = videoValidator.canonicalYouTubeUrl(request.url());
        VideoAnalysis analysis = youTubeVideoUnderstandingProvider.analyzeYouTube(url, descriptionPrompt);
        VideoDescription description = new VideoDescription(
                "YouTube video " + videoId,
                "video/youtube",
                analysis.summary(),
                analysis.transcript(),
                analysis.language(),
                analysis.segments()
        );
        String sourceId = UUID.randomUUID().toString();
        knowledgeService.recordVideoInfo(
                workspace.id(),
                description.filename(),
                null,
                knowledgeText(description),
                new KnowledgeSourceMetadata(
                        sourceId,
                        url,
                        Instant.now(),
                        "ai-workspace-youtube-video-analysis-v1"
                )
        );

        return new YouTubeVideoIngestionResponse(
                sourceId,
                videoId,
                url,
                description.description(),
                description.transcript(),
                description.language(),
                description.segments()
        );
    }

    public String knowledgeText(VideoDescription description) {
        StringBuilder value = new StringBuilder("Visual summary:\n")
                .append(description.description().trim());
        if (description.language() != null && !description.language().isBlank()) {
            value.append("\n\nSpoken language: ").append(description.language().trim());
        }
        if (!description.transcript().isBlank() || !description.segments().isEmpty()) {
            value.append("\n\nSpoken transcript:\n");
            if (description.segments().isEmpty()) {
                value.append(description.transcript().trim());
            } else {
                description.segments().forEach(segment -> value
                        .append('[').append(timestamp(segment.startMilliseconds()))
                        .append(" - ").append(timestamp(segment.endMilliseconds())).append("] ")
                        .append(segment.speaker() == null || segment.speaker().isBlank()
                                ? ""
                                : segment.speaker().trim() + ": ")
                        .append(segment.text().trim()).append('\n'));
            }
        }
        return value.toString().trim();
    }

    private String timestamp(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        long remainder = milliseconds % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, remainder);
    }
}
