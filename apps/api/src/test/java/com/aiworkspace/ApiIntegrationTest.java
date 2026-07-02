package com.aiworkspace;

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
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces"))
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
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/missing"))
                .GET()
                .build());
        JsonNode error = OBJECT_MAPPER.readTree(response.body());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
        assertNotNull(error);
        assertEquals(404, error.path("status").asInt());
        assertEquals("Workspace was not found", error.path("detail").asText());
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
}
