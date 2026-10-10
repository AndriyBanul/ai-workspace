package com.aiworkspace.controllers;

import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.orchestrator.services.OrchestrationSubmissionService;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.users.services.UserAccountService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.HttpStatus.ACCEPTED;

@RestController
@RequestMapping("/api/v1/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {

    private final OrchestratorService orchestratorService;
    private final OrchestrationSubmissionService submissionService;
    private final UserAccountService userAccountService;

    @PostMapping(path = "/ingestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrchestrationSubmission> ingest(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam(value = "document", required = false) MultipartFile document,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "video", required = false) MultipartFile video,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) throws IOException {
        OrchestrationSubmission submission = submissionService.submitUploads(
                userAccountService.getCurrentUserId(authentication),
                workspaceId,
                idempotencyKey,
                toOrchestrationContent(document),
                toOrchestrationContent(audio),
                toOrchestrationContent(image),
                toOrchestrationContent(video)
        );

        return ResponseEntity.status(ACCEPTED).body(submission);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<IngestionJobDetails> getJob(@PathVariable String jobId, Authentication authentication) {
        return ResponseEntity.ok(orchestratorService.findJob(userAccountService.getCurrentUserId(authentication), jobId));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<IngestionJobDetails>> listJobs(@RequestParam String workspaceId,
            @RequestParam(defaultValue = "50") int limit, Authentication authentication) {
        return ResponseEntity.ok(orchestratorService.listJobs(
                userAccountService.getCurrentUserId(authentication), workspaceId, limit));
    }

    private OrchestrationContent toOrchestrationContent(MultipartFile file) throws IOException {
        if (file == null) return null;
        String filename = file.getOriginalFilename();
        if (file.isEmpty() && (filename == null || filename.isBlank())) return null;
        return new OrchestrationContent(filename, file.getContentType(), file.getBytes());
    }
}
