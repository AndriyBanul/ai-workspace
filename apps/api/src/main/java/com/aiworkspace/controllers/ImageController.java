package com.aiworkspace.controllers;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.models.ImageDescriptionResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDescriptionResponse> describe(@RequestParam("file") MultipartFile file) {
        try {
            ImageDescription description = imageService.describe(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );

            return ResponseEntity.ok(new ImageDescriptionResponse(
                    description.filename(),
                    file.getSize(),
                    description.mimeType(),
                    description.description()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while describing image", exception);
        } catch (IOException exception) {
            log.warn("Failed to describe image '{}'", file.getOriginalFilename(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to describe image", exception);
        }
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ImageGenerationRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
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
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while generating image", exception);
        } catch (IOException exception) {
            log.warn("Failed to generate image", exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to generate image", exception);
        }
    }

    public record ImageGenerationRequest(String description) {
    }
}
