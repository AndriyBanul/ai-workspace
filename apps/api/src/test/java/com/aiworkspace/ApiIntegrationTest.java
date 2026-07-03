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
import java.util.List;
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
        HttpResponse<String> created = createWorkspace(owner, "Investor demo");
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
    void storesUploadedDocumentsAsWorkspaceFiles() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        TestUser otherUser = registerUser();
        HttpResponse<String> created = createWorkspace(owner, "Files demo");
        String workspaceId = OBJECT_MAPPER.readTree(created.body()).path("id").asText();

        HttpResponse<String> uploaded = send(HttpRequest.newBuilder(uri("/api/v1/documents/text"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody(
                        "ai-workspace-test",
                        "workspaceId",
                        workspaceId,
                        "file",
                        "sample.txt",
                        "text/plain",
                        "Hello from persisted files."
                ))
                .build());
        HttpResponse<String> files = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId + "/files"))
                .header("Authorization", owner.basicAuthHeader())
                .GET()
                .build());
        HttpResponse<String> otherUserFiles = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId + "/files"))
                .header("Authorization", otherUser.basicAuthHeader())
                .GET()
                .build());
        JsonNode file = OBJECT_MAPPER.readTree(files.body()).path(0);

        assertEquals(HttpStatus.OK.value(), uploaded.statusCode());
        assertEquals(HttpStatus.OK.value(), files.statusCode());
        assertEquals(HttpStatus.NOT_FOUND.value(), otherUserFiles.statusCode());
        assertEquals("sample.txt", file.path("originalFilename").asText());
        assertEquals("text/plain", file.path("contentType").asText());
        assertEquals("DOCUMENT", file.path("sourceType").asText());
        assertEquals("PROCESSED", file.path("status").asText());
        assertEquals("Hello from persisted files.".getBytes(StandardCharsets.UTF_8).length,
                file.path("sizeBytes").asLong());
        assertTrue(file.path("storageKey").asText().contains(workspaceId));
        assertEquals(64, file.path("checksumSha256").asText().length());
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

    private HttpResponse<String> createWorkspace(TestUser user, String name) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/v1/workspaces"))
                .header("Authorization", user.basicAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
                .build());
    }

    private HttpRequest.BodyPublisher multipartBody(
            String boundary,
            String textName,
            String textValue,
            String fileName,
            String originalFilename,
            String contentType,
            String fileContent
    ) {
        String separator = "--" + boundary + "\r\n";
        String ending = "--" + boundary + "--\r\n";
        List<byte[]> parts = List.of(
                separator.getBytes(StandardCharsets.UTF_8),
                ("Content-Disposition: form-data; name=\"" + textName + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8),
                textValue.getBytes(StandardCharsets.UTF_8),
                "\r\n".getBytes(StandardCharsets.UTF_8),
                separator.getBytes(StandardCharsets.UTF_8),
                ("Content-Disposition: form-data; name=\"" + fileName + "\"; filename=\"" + originalFilename
                        + "\"\r\n").getBytes(StandardCharsets.UTF_8),
                ("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8),
                fileContent.getBytes(StandardCharsets.UTF_8),
                "\r\n".getBytes(StandardCharsets.UTF_8),
                ending.getBytes(StandardCharsets.UTF_8)
        );

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private record TestUser(String id, String email, String password) {

        String basicAuthHeader() {
            String credentials = email + ":" + password;
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
    }
}
