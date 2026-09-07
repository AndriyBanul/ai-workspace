package com.aiworkspace.config;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.models.ApiErrorResponse;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentProcessing(
            DocumentProcessingException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.code()) {
            case EMPTY_DOCUMENT -> HttpStatus.BAD_REQUEST;
            case UNSUPPORTED_DOCUMENT_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case PASSWORD_PROTECTED_DOCUMENT, CORRUPT_DOCUMENT, EXTRACTION_LIMIT_EXCEEDED,
                    NO_EXTRACTABLE_TEXT -> HttpStatus.valueOf(422);
        };
        return error(status, exception.getMessage(), request, exception.code().name());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(status, detail(exception), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, detail(exception), request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoSuchElementException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, detail(exception), request);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiErrorResponse> handleIo(IOException exception, HttpServletRequest request) {
        log.warn("I/O failure while handling {}", request.getRequestURI(), exception);
        return error(HttpStatus.BAD_GATEWAY, detail(exception), request);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstream(
            UpstreamServiceException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Upstream service failure from {} while handling {}",
                exception.serviceName(),
                request.getRequestURI(),
                exception
        );
        return error(HttpStatus.BAD_GATEWAY, detail(exception), request);
    }

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<ApiErrorResponse> handleInterrupted(
            InterruptedException exception,
            HttpServletRequest request
    ) {
        Thread.currentThread().interrupt();
        log.warn("Interrupted while handling {}", request.getRequestURI(), exception);
        return error(HttpStatus.BAD_GATEWAY, "Request was interrupted", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected failure while handling {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String detail, HttpServletRequest request) {
        return error(status, detail, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String detail,
            HttpServletRequest request,
            String code
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                detail,
                request.getRequestURI(),
                code
        ));
    }

    private String detail(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }

    private String detail(ResponseStatusException exception) {
        String reason = exception.getReason();
        if (reason == null || reason.isBlank()) {
            return exception.getStatusCode().toString();
        }

        return reason;
    }
}
