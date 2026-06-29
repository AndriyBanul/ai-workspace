package com.aiworkspace.controllers;

import com.aiworkspace.documents.domain.ParsedTextDocument;
import com.aiworkspace.documents.services.TextDocumentParserService;
import java.io.IOException;

import com.aiworkspace.models.TextDocumentUploadResponse;
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/documents")
public class TextDocumentController {

    private static final Logger log = LoggerFactory.getLogger(TextDocumentController.class);

    private final TextDocumentParserService textDocumentParserService;

    public TextDocumentController(TextDocumentParserService textDocumentParserService) {
        this.textDocumentParserService = textDocumentParserService;
    }

    @PostMapping(path = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TextDocumentUploadResponse> uploadTextDocument(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "File must not be empty");
        }

        ParsedTextDocument document = textDocumentParserService.parse(file.getOriginalFilename(), file.getBytes());

        log.info("Parsed text document '{}':\n{}", document.filename(), document.content());

        return ResponseEntity.ok(new TextDocumentUploadResponse(
                document.filename(),
                file.getSize(),
                document.content().length()
        ));
    }

}
