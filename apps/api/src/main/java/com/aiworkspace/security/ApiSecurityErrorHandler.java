package com.aiworkspace.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiSecurityResponseWriter responseWriter;
    private final SecurityAuditService auditService;

    public ApiSecurityErrorHandler(ApiSecurityResponseWriter responseWriter, SecurityAuditService auditService) {
        this.responseWriter = responseWriter;
        this.auditService = auditService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        auditService.record("AUTHENTICATION_FAILURE", "DENIED", null, request, HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ai-workspace\"");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentication is required", "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        auditService.record("AUTHORIZATION_FAILURE", "DENIED", authentication, request, HttpStatus.FORBIDDEN.value());
        responseWriter.write(request, response, HttpStatus.FORBIDDEN,
                "Access is denied", "ACCESS_DENIED");
    }
}
