package com.aiworkspace.controllers;

import com.aiworkspace.documents.models.WebPageExtractRequest;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.users.services.UserAccountService;
import java.io.IOException;
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
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final OrchestratorService orchestratorService;
    private final UserAccountService userAccountService;

    public DocumentController(OrchestratorService orchestratorService, UserAccountService userAccountService) {
        this.orchestratorService = orchestratorService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TextDocumentUploadResponse> uploadTextDocument(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        return ResponseEntity.ok(orchestratorService.ingestDocument(
                    userAccountService.currentUserId(authentication),
                    workspaceId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
        ));
    }

    @PostMapping("/web-page")
    public ResponseEntity<WebPageExtractResponse> extractWebPage(
            @RequestBody WebPageExtractRequest request,
            Authentication authentication
    )
            throws IOException {
        return ResponseEntity
                .ok(orchestratorService.ingestWebPage(userAccountService.currentUserId(authentication), request));
    }
}
