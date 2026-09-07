package com.aiworkspace.documents.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.WebPageExtractRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentValidator {

    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    public void validateUploadContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new DocumentProcessingException(DocumentFailureCode.EMPTY_DOCUMENT);
        }
    }

    public void validateDetectedContentType(String contentType) {
        if (!SUPPORTED_MEDIA_TYPES.contains(contentType)) {
            throw new DocumentProcessingException(DocumentFailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
        }
    }

    public void validateExtractedText(String content) {
        if (content == null || content.isBlank()) {
            throw new DocumentProcessingException(DocumentFailureCode.NO_EXTRACTABLE_TEXT);
        }
    }

    public void validateWebPageExtractRequest(WebPageExtractRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }
}
