package com.aiworkspace.config;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentFailureCode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void exposesPasswordProtectedDocumentFailureWithoutParserDetails() {
        DocumentProcessingException exception = new DocumentProcessingException(
                DocumentFailureCode.PASSWORD_PROTECTED_DOCUMENT,
                new IOException("Internal parser details")
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/text");

        var response = new GlobalExceptionHandler().handleDocumentProcessing(exception, request);

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PASSWORD_PROTECTED_DOCUMENT", response.getBody().code());
        assertEquals(exception.getMessage(), response.getBody().detail());
        assertFalse(response.getBody().detail().contains("Internal parser details"));
        assertEquals("/api/v1/documents/text", response.getBody().path());
    }
}
