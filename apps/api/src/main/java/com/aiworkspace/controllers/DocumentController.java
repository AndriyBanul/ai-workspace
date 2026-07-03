package com.aiworkspace.controllers;

import com.aiworkspace.config.CurrentUserService;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.files.models.CreateWorkspaceFileRequest;
import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.models.WorkspaceFileSourceType;
import com.aiworkspace.files.services.WorkspaceFileService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.TextDocumentUploadResponse;
import com.aiworkspace.workspaces.models.Workspace;
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
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    public DocumentController(
            DocumentService documentService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            CurrentUserService currentUserService
    ) {
        this.documentService = documentService;
        this.workspaceFileService = workspaceFileService;
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

        Workspace workspace = workspaceService.getWorkspace(currentUserService.currentUserId(authentication), workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .content(file.getInputStream())
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        ParsedTextDocument document;
        try {
            document = documentService.parseTextDocument(file.getOriginalFilename(), file.getBytes());
            knowledgeService.recordDocumentsInfo(workspace.id(), document.filename(), null, document.content());
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

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
