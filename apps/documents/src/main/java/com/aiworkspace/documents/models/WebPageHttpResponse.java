package com.aiworkspace.documents.models;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record WebPageHttpResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body) {
}
