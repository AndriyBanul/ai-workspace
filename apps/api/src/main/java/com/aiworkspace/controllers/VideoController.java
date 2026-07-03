package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.files.models.CreateWorkspaceFileRequest;
import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.models.WorkspaceFileSourceType;
import com.aiworkspace.files.services.WorkspaceFileService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {

    private final VideoService videoService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public VideoController(
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.videoService = videoService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDescriptionResponse> describe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.VIDEO)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .content(file.getInputStream())
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        VideoDescription description;
        try {
            description = videoService.describe(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
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

        return ResponseEntity.ok(new VideoDescriptionResponse(
                description.filename(),
                file.getSize(),
                description.mimeType(),
                description.description()
        ));
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody VideoGenerationRequest request)
            throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        GeneratedVideo video = videoService.generate(request.description());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(video.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(video.filename())
                                .build()
                                .toString()
                )
                .body(video.content());
    }

    public record VideoGenerationRequest(String description) {
    }
}
