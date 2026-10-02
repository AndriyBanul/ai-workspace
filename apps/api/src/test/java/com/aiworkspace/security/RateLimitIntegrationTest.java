package com.aiworkspace.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:rate_limit_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "ai-workspace.security.rate-limit.enabled=true",
                "ai-workspace.security.rate-limit.requests-per-minute=2",
                "ai-workspace.security.rate-limit.registration-requests-per-minute=2"
        }
)
class RateLimitIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void returnsStructuredTooManyRequestsResponse() throws IOException, InterruptedException {
        HttpResponse<String> first = request();
        HttpResponse<String> second = request();
        HttpResponse<String> denied = request();
        JsonNode error = OBJECT_MAPPER.readTree(denied.body());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), first.statusCode());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), second.statusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), denied.statusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", error.path("code").asText());
        assertEquals("/api/v1/workspaces", error.path("path").asText());
        assertTrue(denied.headers().firstValue("Retry-After").map(Long::parseLong).orElse(0L) > 0);
    }

    private HttpResponse<String> request() throws IOException, InterruptedException {
        return HTTP_CLIENT.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/workspaces"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
