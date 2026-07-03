package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.ImageDescriptionResponse;
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
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public ImageController(
            ImageService imageService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.imageService = imageService;
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
        workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        ImageDescription description = imageService.describe(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        knowledgeService.recordImagesInfo(workspaceId, description.filename(), null, description.description());

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
