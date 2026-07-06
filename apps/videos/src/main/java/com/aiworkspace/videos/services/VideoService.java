package com.aiworkspace.videos.services;

import com.aiworkspace.videos.client.GeminiVideoClient;
import com.aiworkspace.videos.client.VeoVideoClient;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_GENERATION_DESCRIPTION_LENGTH = 4_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "mov", "webm", "mpeg", "mpg", "avi");
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime",
            "webm", "video/webm",
            "mpeg", "video/mpeg",
            "mpg", "video/mpeg",
            "avi", "video/x-msvideo"
    );

    private final GeminiVideoClient geminiVideoClient;
    private final VeoVideoClient veoVideoClient;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final String descriptionPrompt;

    public VideoService(
            GeminiVideoClient geminiVideoClient,
            VeoVideoClient veoVideoClient,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt
    ) {
        this(geminiVideoClient, veoVideoClient, null, null, null, descriptionPrompt);
    }

    @Autowired
    public VideoService(
            GeminiVideoClient geminiVideoClient,
            VeoVideoClient veoVideoClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt
    ) {
        this.geminiVideoClient = geminiVideoClient;
        this.veoVideoClient = veoVideoClient;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.descriptionPrompt = descriptionPrompt;
    }

    public VideoDescription describe(String filename, String contentType, byte[] videoContent)
            throws IOException, InterruptedException {
        validateVideo(filename, videoContent);

        String mimeType = mimeType(filename, contentType);
        String description = geminiVideoClient.describe(videoContent, mimeType, descriptionPrompt);

        return new VideoDescription(filename, mimeType, description);
    }

    public GeneratedVideo generate(String description) throws IOException, InterruptedException {
        validateGenerationDescription(description);

        return veoVideoClient.generate(description.trim());
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
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return generate(request.description());
    }

    private void validateVideo(String filename, byte[] videoContent) {
        if (videoContent == null || videoContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (videoContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 20MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only MP4, MOV, WEBM, MPEG, MPG, and AVI videos are supported");
        }
    }

    private void validateGenerationDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }

        if (description.length() > MAX_GENERATION_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must not be longer than 4000 characters");
        }
    }

    private String mimeType(String filename, String contentType) {
        String mimeType = MIME_TYPES_BY_EXTENSION.get(extension(filename));
        if (contentType != null && contentType.equals(mimeType)) {
            return contentType;
        }

        return mimeType;
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
