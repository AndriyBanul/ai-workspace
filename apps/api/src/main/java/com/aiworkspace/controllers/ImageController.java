package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.files.models.CreateWorkspaceFileRequest;
import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.models.WorkspaceFileSourceType;
import com.aiworkspace.files.services.WorkspaceFileService;
import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.ImageDescriptionResponse;
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
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public ImageController(
            ImageService imageService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.imageService = imageService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDescriptionResponse> describe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.IMAGE)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .content(file.getInputStream())
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        ImageDescription description;
        try {
            description = imageService.describe(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            knowledgeService.recordImagesInfo(workspace.id(), description.filename(), null, description.description());
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        return ResponseEntity.ok(new ImageDescriptionResponse(
                description.filename(),
                file.getSize(),
                description.mimeType(),
                description.description()
        ));
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ImageGenerationRequest request)
            throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        GeneratedImage image = imageService.generate(request.description());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(image.filename())
                                .build()
                                .toString()
                )
                .body(image.content());
    }

    public record ImageGenerationRequest(String description) {
    }
}
