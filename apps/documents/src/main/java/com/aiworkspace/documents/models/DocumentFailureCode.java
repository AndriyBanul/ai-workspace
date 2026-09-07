package com.aiworkspace.documents.models;

public enum DocumentFailureCode {
    EMPTY_DOCUMENT("Document must not be empty."),
    UNSUPPORTED_DOCUMENT_FORMAT("Unsupported document format. Supported formats: TXT, PDF, DOCX, XLSX, PPTX."),
    PASSWORD_PROTECTED_DOCUMENT("Document is password-protected. Upload an unencrypted copy."),
    CORRUPT_DOCUMENT("Document could not be read. It may be corrupt or invalid; export it again and retry."),
    EXTRACTION_LIMIT_EXCEEDED("Document contains more extractable text than the configured limit. Split it into smaller files."),
    NO_EXTRACTABLE_TEXT("Document contains no extractable text. Scanned documents may require OCR, which is not supported yet.");

    private final String message;

    DocumentFailureCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
