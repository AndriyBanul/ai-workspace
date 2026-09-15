package com.aiworkspace.security;

import com.aiworkspace.config.SecurityProperties;
import com.aiworkspace.security.InMemoryRateLimiter.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final SecurityProperties properties;
    private final InMemoryRateLimiter rateLimiter;
    private final ApiSecurityResponseWriter responseWriter;
    private final SecurityAuditService auditService;

    public ApiRateLimitFilter(SecurityProperties properties, InMemoryRateLimiter rateLimiter,
            ApiSecurityResponseWriter responseWriter, SecurityAuditService auditService) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.responseWriter = responseWriter;
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || !properties.rateLimit().enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean registration = REGISTER_PATH.equals(request.getRequestURI());
        int limit = registration
                ? properties.rateLimit().registrationRequestsPerMinute()
                : properties.rateLimit().requestsPerMinute();
        String scope = registration ? "registration" : "api";
        RateLimitDecision decision = rateLimiter.acquire(scope + ':' + clientFingerprint(request), limit);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        auditService.record("RATE_LIMIT_EXCEEDED", "DENIED", null, request, HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        responseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests", "RATE_LIMIT_EXCEEDED");
    }

    private String clientFingerprint(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) {
            address = "unknown";
        }
        return SecurityFingerprint.of(address);
    }
}
