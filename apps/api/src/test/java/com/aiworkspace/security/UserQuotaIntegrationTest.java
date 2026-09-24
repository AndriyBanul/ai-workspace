package com.aiworkspace.security;

import com.aiworkspace.users.services.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:user_quota_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "ai-workspace.security.rate-limit.enabled=false",
        "ai-workspace.security.user-quota.ingestions-per-day=1",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
class UserQuotaIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountService users;

    @Test
    void limitsCostlyRequestsPerAuthenticatedUserAndEchoesSafeRequestId()
            throws IOException, InterruptedException {
        String email = UUID.randomUUID() + "@example.com";
        String password = "ExamplePassword123!";
        users.register(email, password, "Quota user");
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                (email + ':' + password).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> first = send(basic);
        HttpResponse<String> denied = send(basic);

        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), first.statusCode());
        assertEquals("quota-test-1", first.headers().firstValue("X-Request-ID").orElse(null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.statusCode());
        assertEquals("USER_QUOTA_EXCEEDED",
                new ObjectMapper().readTree(denied.body()).path("code").asText());
        assertTrue(denied.headers().firstValue("Retry-After").map(Long::parseLong).orElse(0L) > 0);

        String otherEmail = UUID.randomUUID() + "@example.com";
        users.register(otherEmail, password, "Another user");
        String otherBasic = "Basic " + Base64.getEncoder().encodeToString(
                (otherEmail + ':' + password).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> otherUser = send(otherBasic, "invalid request id!");
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), otherUser.statusCode());
        assertFalse("invalid request id!".equals(
                otherUser.headers().firstValue("X-Request-ID").orElse(null)));

        URI metrics = URI.create("http://localhost:" + port + "/actuator/metrics");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), HTTP.send(HttpRequest.newBuilder(metrics).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(HttpStatus.OK.value(), HTTP.send(HttpRequest.newBuilder(metrics)
                .header("Authorization", basic).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/not-a-real-route"))
                .header("Authorization", basic).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private HttpResponse<String> send(String authorization) throws IOException, InterruptedException {
        return send(authorization, "quota-test-1");
    }

    private HttpResponse<String> send(String authorization, String requestId) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/documents/web-page"))
                .header("Authorization", authorization)
                .header("X-Request-ID", requestId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
