package com.aiworkspace.controllers;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.images.models.ImageGenerationRequest;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.users.services.UserAccountService;
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
    private final OrchestratorService orchestratorService;
    private final UserAccountService userAccountService;

    public ImageController(ImageService imageService, OrchestratorService orchestratorService,
            UserAccountService userAccountService) {
        this.imageService = imageService;
        this.orchestratorService = orchestratorService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDescriptionResponse> describe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        return ResponseEntity.ok(orchestratorService.ingestImage(
                userAccountService.currentUserId(authentication),
                workspaceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        ));
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ImageGenerationRequest request)
            throws IOException, InterruptedException {
        GeneratedImage image = imageService.generate(request);

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
}
