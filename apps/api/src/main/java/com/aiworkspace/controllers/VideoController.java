package com.aiworkspace.controllers;

import com.aiworkspace.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
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
@RequestMapping("/api/v1/videos")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDescriptionResponse> describe(@RequestParam("file") MultipartFile file) {
        try {
            VideoDescription description = videoService.describe(
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

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody VideoGenerationRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
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
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while generating video", exception);
        } catch (IOException exception) {
            log.warn("Failed to generate video", exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to generate video", exception);
        }
    }

    public record VideoGenerationRequest(String description) {
    }
}
