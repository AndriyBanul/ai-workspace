package com.aiworkspace.controllers;

import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;
import com.aiworkspace.users.services.UserAccountService;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.orchestrator.services.OrchestratorService;
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
    private final OrchestratorService orchestratorService;
    private final UserAccountService userAccountService;

    public VideoController(VideoService videoService, OrchestratorService orchestratorService,
            UserAccountService userAccountService) {
        this.videoService = videoService;
        this.orchestratorService = orchestratorService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoDescriptionResponse> describe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        return ResponseEntity.ok(
                orchestratorService.ingestVideo(
                userAccountService.currentUserId(authentication),
                workspaceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        ));
    }

    @PostMapping(path = "/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody VideoGenerationRequest request)
            throws IOException, InterruptedException {
        GeneratedVideo video = videoService.generate(request);

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

    @PostMapping(path = "/youtube", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<YouTubeVideoIngestionResponse> ingestYouTube(
            @RequestBody YouTubeVideoIngestionRequest request,
            Authentication authentication
    ) throws IOException, InterruptedException {
        return ResponseEntity.ok(orchestratorService.ingestYouTube(
                userAccountService.currentUserId(authentication),
                request
        ));
    }
}
