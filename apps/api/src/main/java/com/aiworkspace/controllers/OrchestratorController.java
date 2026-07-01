package com.aiworkspace.controllers;

import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.orchestrator.services.OrchestratorService;
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

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);

    private final OrchestratorService orchestratorService;

    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping(path = "/ingestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrchestrationSubmission> ingest(
            @RequestParam(value = "document", required = false) MultipartFile document,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "video", required = false) MultipartFile video
    ) {
        try {
            OrchestrationSubmission submission = orchestratorService.process(
                    contentFrom(document),
                    contentFrom(audio),
                    contentFrom(image),
                    contentFrom(video)
            );

            return ResponseEntity.status(ACCEPTED).body(submission);
        } catch (IOException exception) {
            log.warn("Failed to read orchestration upload", exception);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to read uploaded content", exception);
        }
    }

    private OrchestrationContent contentFrom(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        return new OrchestrationContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }
}
