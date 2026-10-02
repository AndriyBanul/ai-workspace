package com.aiworkspace.documents.exceptions;

import com.aiworkspace.documents.models.DocumentFailureCode;
import java.util.Objects;

public class DocumentProcessingException extends RuntimeException {

    private final DocumentFailureCode code;

    public DocumentProcessingException(DocumentFailureCode code) {
        this(code, null);
    }

    public DocumentProcessingException(DocumentFailureCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code must not be null").message(), cause);
        this.code = code;
    }

    public DocumentFailureCode code() {
        return code;
    }
}
