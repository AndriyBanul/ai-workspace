package com.aiworkspace.controllers;

import com.aiworkspace.documents.domain.ExtractedWebPage;
import com.aiworkspace.documents.services.WebPageTextExtractor;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/documents")
public class WebPageController {

    private static final int MAX_LOGGED_CHARACTERS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(WebPageController.class);

    private final WebPageTextExtractor webPageTextExtractor;

    public WebPageController(WebPageTextExtractor webPageTextExtractor) {
        this.webPageTextExtractor = webPageTextExtractor;
    }

    @PostMapping("/web-page")
    public ResponseEntity<WebPageExtractResponse> extractWebPage(@RequestBody WebPageExtractRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
            ExtractedWebPage page = webPageTextExtractor.extract(request.url());
            String loggedContent = contentForLog(page.content());
            boolean truncated = loggedContent.length() < page.content().length();

            log.info("Extracted web page '{}' from '{}':\n{}", page.title(), page.url(), loggedContent);

            return ResponseEntity.ok(new WebPageExtractResponse(
                    page.url(),
                    page.title(),
                    page.content().length(),
                    loggedContent.length(),
                    truncated
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while fetching web page", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch web page", exception);
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
