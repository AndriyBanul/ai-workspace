package com.aiworkspace.controllers;

import com.aiworkspace.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoDescriptionService;
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
@RequestMapping("/api/v1/videos")
public class VideoDescriptionController {

    private static final Logger log = LoggerFactory.getLogger(VideoDescriptionController.class);

    private final VideoDescriptionService videoDescriptionService;

    public VideoDescriptionController(VideoDescriptionService videoDescriptionService) {
        this.videoDescriptionService = videoDescriptionService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDescriptionResponse> describe(@RequestParam("file") MultipartFile file) {
        try {
            VideoDescription description = videoDescriptionService.describe(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );

            return ResponseEntity.ok(new VideoDescriptionResponse(
                    description.filename(),
                    file.getSize(),
                    description.mimeType(),
                    description.description()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while describing video", exception);
        } catch (IOException exception) {
            log.warn("Failed to describe video '{}'", file.getOriginalFilename(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to describe video", exception);
        }
    }
}
