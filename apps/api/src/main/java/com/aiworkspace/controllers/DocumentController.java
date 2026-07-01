package com.aiworkspace.controllers;

import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.TextDocumentUploadResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final int MAX_LOGGED_CHARACTERS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final KnowledgeService knowledgeService;

    public DocumentController(DocumentService documentService, KnowledgeService knowledgeService) {
        this.documentService = documentService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(path = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TextDocumentUploadResponse> uploadTextDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "File must not be empty");
        }

        try {
            ParsedTextDocument document = documentService.parseTextDocument(file.getOriginalFilename(), file.getBytes());
            knowledgeService.recordDocumentsInfo(document.content());

            log.info("Parsed text document '{}':\n{}", document.filename(), document.content());

            return ResponseEntity.ok(new TextDocumentUploadResponse(
                    document.filename(),
                    file.getSize(),
                    document.content().length()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            log.warn("Failed to parse or store text document '{}'", file.getOriginalFilename(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse or store text document", exception);
        }
    }

    @PostMapping("/web-page")
    public ResponseEntity<WebPageExtractResponse> extractWebPage(@RequestBody WebPageExtractRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
            ExtractedWebPage page = documentService.extractWebPage(request.url());
            String loggedContent = contentForLog(page.content());
            boolean truncated = loggedContent.length() < page.content().length();

            log.info("Extracted web page '{}' from '{}':\n{}", page.title(), page.url(), loggedContent);
            knowledgeService.recordDocumentsInfo(page.content());

            return ResponseEntity.ok(new WebPageExtractResponse(
                    page.url(),
                    page.title(),
                    page.content().length(),
                    loggedContent.length(),
                    truncated
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch or store web page", exception);
        }
    }

    private String contentForLog(String content) {
        if (content.length() <= MAX_LOGGED_CHARACTERS) {
            return content;
        }

        return content.substring(0, MAX_LOGGED_CHARACTERS);
    }

    public record WebPageExtractRequest(String url) {
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
