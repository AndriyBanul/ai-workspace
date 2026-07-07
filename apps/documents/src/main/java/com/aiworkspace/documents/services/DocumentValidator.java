package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.WebPageExtractRequest;
import org.springframework.stereotype.Component;

@Component
public class DocumentValidator {

    public void validateUploadContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
    }

    public void validateWebPageExtractRequest(WebPageExtractRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }
}
