package com.aiworkspace.controllers;

import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageDescriptionService;
import com.aiworkspace.models.ImageDescriptionResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/images")
public class ImageDescriptionController {

    private static final Logger log = LoggerFactory.getLogger(ImageDescriptionController.class);

    private final ImageDescriptionService imageDescriptionService;

    public ImageDescriptionController(ImageDescriptionService imageDescriptionService) {
        this.imageDescriptionService = imageDescriptionService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDescriptionResponse> describe(@RequestParam("file") MultipartFile file) {
        try {
            ImageDescription description = imageDescriptionService.describe(
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
}
