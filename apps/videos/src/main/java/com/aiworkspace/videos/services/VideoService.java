package com.aiworkspace.videos.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.videos.interfaces.VideoGenerationProvider;
import com.aiworkspace.videos.interfaces.VideoUnderstandingProvider;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

    private final VideoUnderstandingProvider videoUnderstandingProvider;
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
        this(videoUnderstandingProvider, videoGenerationProvider, null, null, null, descriptionPrompt, new VideoValidator());
    }

    @Autowired
    public VideoService(
            VideoUnderstandingProvider videoUnderstandingProvider,
            VideoGenerationProvider videoGenerationProvider,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt,
            VideoValidator videoValidator
    ) {
        this.videoUnderstandingProvider = videoUnderstandingProvider;
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
        String description = videoUnderstandingProvider.describe(videoContent, mimeType, descriptionPrompt);

        return new VideoDescription(filename, mimeType, description);
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
            knowledgeService.recordVideoInfo(workspace.id(), description.filename(), null, description.description());
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
                description.description()
        );
    }

    public GeneratedVideo generate(VideoGenerationRequest request) throws IOException, InterruptedException {
        videoValidator.validateGenerationRequest(request);

        return generate(request.description());
    }
}
