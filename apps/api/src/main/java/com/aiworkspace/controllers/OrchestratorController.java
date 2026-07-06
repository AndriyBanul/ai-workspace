package com.aiworkspace.controllers;

import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.users.services.UserAccountService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.HttpStatus.ACCEPTED;

@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;
    private final UserAccountService userAccountService;

    public OrchestratorController(OrchestratorService orchestratorService, UserAccountService userAccountService) {
        this.orchestratorService = orchestratorService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/ingestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrchestrationSubmission> ingest(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam(value = "document", required = false) MultipartFile document,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "video", required = false) MultipartFile video,
            Authentication authentication
    ) throws IOException {
        OrchestrationSubmission submission = orchestratorService.process(
                userAccountService.currentUserId(authentication),
                workspaceId,
                contentFrom(document),
                contentFrom(audio),
                contentFrom(image),
                contentFrom(video)
        );

        return ResponseEntity.status(ACCEPTED).body(submission);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<IngestionJobDetails> getJob(@PathVariable String jobId, Authentication authentication) {
        return ResponseEntity.ok(orchestratorService.findJob(userAccountService.currentUserId(authentication), jobId));
    }

    private OrchestrationContent contentFrom(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        return new OrchestrationContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }
}
