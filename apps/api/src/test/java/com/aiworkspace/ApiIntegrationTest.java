package com.aiworkspace;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:api_integration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
class ApiIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void mapsValidationErrorsToStandardErrorResponse() throws IOException, InterruptedException {
        TestUser user = registerUser();
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces"))
                .header("Authorization", user.basicAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build());
        JsonNode error = OBJECT_MAPPER.readTree(response.body());

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertNotNull(error);
        assertEquals(400, error.path("status").asInt());
        assertEquals("Workspace name must not be blank", error.path("detail").asText());
        assertEquals("/api/v1/workspaces", error.path("path").asText());
    }

    @Test
    void mapsMissingResourcesToStandardErrorResponse() throws IOException, InterruptedException {
        TestUser user = registerUser();
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/missing"))
                .header("Authorization", user.basicAuthHeader())
                .GET()
                .build());
        JsonNode error = OBJECT_MAPPER.readTree(response.body());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
        assertNotNull(error);
        assertEquals(404, error.path("status").asInt());
        assertEquals("Workspace was not found", error.path("detail").asText());
    }

    @Test
    void requiresAuthenticationForApiEndpoints() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces"))
                .GET()
                .build());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
    }

    @Test
    void scopesWorkspacesToAuthenticatedOwner() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        TestUser otherUser = registerUser();
        HttpResponse<String> created = send(HttpRequest.newBuilder(uri("/api/v1/workspaces"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Investor demo\"}"))
                .build());
        JsonNode workspace = OBJECT_MAPPER.readTree(created.body());
        String workspaceId = workspace.path("id").asText();

        HttpResponse<String> ownerResponse = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId))
                .header("Authorization", owner.basicAuthHeader())
                .GET()
                .build());
        HttpResponse<String> otherUserResponse = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId))
                .header("Authorization", otherUser.basicAuthHeader())
                .GET()
                .build());

        assertEquals(HttpStatus.CREATED.value(), created.statusCode());
        assertEquals(owner.id(), workspace.path("ownerId").asText());
        assertEquals(HttpStatus.OK.value(), ownerResponse.statusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), otherUserResponse.statusCode());
    }

    @Test
    void servesSwaggerUiAndOpenApiSpec() throws IOException, InterruptedException {
        HttpResponse<String> swagger = send(HttpRequest.newBuilder(uri("/swagger-ui.html")).GET().build());
        HttpResponse<String> openApi = send(HttpRequest.newBuilder(uri("/openapi.yaml")).GET().build());

        assertEquals(HttpStatus.OK.value(), swagger.statusCode());
        assertTrue(swagger.body().contains("SwaggerUIBundle"));
        assertEquals(HttpStatus.OK.value(), openApi.statusCode());
        assertTrue(openApi.body().contains("AI Workspace API"));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private TestUser registerUser() throws IOException, InterruptedException {
        String email = UUID.randomUUID() + "@example.com";
        String password = "password123";
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "displayName": "Test User"
                        }
                        """.formatted(email, password)))
                .build());

        assertEquals(HttpStatus.CREATED.value(), response.statusCode());
        JsonNode user = OBJECT_MAPPER.readTree(response.body());
        return new TestUser(user.path("id").asText(), email, password);
    }

    private record TestUser(String id, String email, String password) {

        String basicAuthHeader() {
            String credentials = email + ":" + password;
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
    }
}
