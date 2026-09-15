package com.aiworkspace.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final int MAX_PATH_LENGTH = 512;

    public void record(String eventType, String outcome, Authentication authentication,
            HttpServletRequest request, int status) {
        audit.info(
                "security_event eventId={} type={} outcome={} actor={} method={} path={} status={} client={}",
                UUID.randomUUID(), safeToken(eventType), safeToken(outcome), actorFingerprint(authentication),
                safeToken(request.getMethod()), safePath(request.getRequestURI()), status,
                SecurityFingerprint.of(request.getRemoteAddr()));
    }

    private String actorFingerprint(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            return "anonymous";
        }
        return SecurityFingerprint.of(authentication.getName().trim().toLowerCase());
    }

    private String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String safePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String sanitized = path.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= MAX_PATH_LENGTH ? sanitized : sanitized.substring(0, MAX_PATH_LENGTH);
    }

}
