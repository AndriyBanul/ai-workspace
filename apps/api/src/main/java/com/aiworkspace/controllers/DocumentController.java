package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.TextDocumentUploadResponse;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final int MAX_LOGGED_CHARACTERS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public DocumentController(
            DocumentService documentService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.documentService = documentService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(path = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TextDocumentUploadResponse> uploadTextDocument(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        ParsedTextDocument document = documentService.parseTextDocument(file.getOriginalFilename(), file.getBytes());
        knowledgeService.recordDocumentsInfo(workspaceId, document.filename(), null, document.content());

        log.info("Parsed text document '{}':\n{}", document.filename(), document.content());

        return ResponseEntity.ok(new TextDocumentUploadResponse(
                document.filename(),
                file.getSize(),
                document.content().length()
        ));
    }

    @PostMapping("/web-page")
    public ResponseEntity<WebPageExtractResponse> extractWebPage(
            @RequestBody WebPageExtractRequest request,
            Authentication authentication
    )
            throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        workspaceService.getWorkspace(currentUserService.currentUserId(authentication), request.workspaceId());
        ExtractedWebPage page = documentService.extractWebPage(request.url());
        String loggedContent = contentForLog(page.content());
        boolean truncated = loggedContent.length() < page.content().length();

        log.info("Extracted web page '{}' from '{}':\n{}", page.title(), page.url(), loggedContent);
        knowledgeService.recordDocumentsInfo(request.workspaceId(), page.title(), null, page.content());

        return ResponseEntity.ok(new WebPageExtractResponse(
                page.url(),
                page.title(),
                page.content().length(),
                loggedContent.length(),
                truncated
        ));
    }

    private String contentForLog(String content) {
        if (content.length() <= MAX_LOGGED_CHARACTERS) {
            return content;
        }

        return content.substring(0, MAX_LOGGED_CHARACTERS);
    }

    public record WebPageExtractRequest(String workspaceId, String url) {
    }

    public record WebPageExtractResponse(
            String url,
            String title,
            int characterCount,
            int loggedCharacterCount,
            boolean truncated
    ) {
    }
}
