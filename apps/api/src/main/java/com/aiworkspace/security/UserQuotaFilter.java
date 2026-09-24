package com.aiworkspace.security;

import com.aiworkspace.config.SecurityProperties;
import com.aiworkspace.security.UserQuotaService.Action;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserQuotaFilter extends OncePerRequestFilter {

    private final UserQuotaService quotas;
    private final SecurityProperties properties;
    private final ApiSecurityResponseWriter responses;
    private final SecurityAuditService audit;

    public UserQuotaFilter(UserQuotaService quotas, SecurityProperties properties,
            ApiSecurityResponseWriter responses, SecurityAuditService audit) {
        this.quotas = quotas;
        this.properties = properties;
        this.responses = responses;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.userQuota().enabled() || !"POST".equals(request.getMethod())
                || actionFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }
        var decision = quotas.acquire(authentication.getName(), actionFor(request.getRequestURI()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        audit.record("USER_QUOTA_EXCEEDED", "DENIED", authentication, request, HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        responses.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                "Daily user quota reached", "USER_QUOTA_EXCEEDED");
    }

    private Action actionFor(String path) {
        if (path.startsWith("/api/v1/knowledge/") && path.endsWith("/answers")) return Action.ANSWER;
        if (path.endsWith("/generations") || path.equals("/api/v1/audio/speech")) return Action.GENERATION;
        if (path.equals("/api/v1/orchestrator/ingestions") || path.endsWith("/reprocess")
                || path.equals("/api/v1/documents/text") || path.equals("/api/v1/documents/web-page")
                || path.equals("/api/v1/audio/transcriptions")
                || path.equals("/api/v1/images/descriptions")
                || path.equals("/api/v1/videos/descriptions")
                || path.equals("/api/v1/videos/youtube")) return Action.INGESTION;
        return null;
    }
}
