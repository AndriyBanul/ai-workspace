package com.aiworkspace.controllers;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.services.ImageGenerationService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/images")
public class ImageGenerationController {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationController.class);

    private final ImageGenerationService imageGenerationService;

    public ImageGenerationController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ImageGenerationRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
            GeneratedImage image = imageGenerationService.generate(request.description());

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
