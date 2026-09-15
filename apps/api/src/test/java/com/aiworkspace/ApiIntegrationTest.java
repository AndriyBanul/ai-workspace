package com.aiworkspace;

import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.aiworkspace.videos.interfaces.YouTubeVideoUnderstandingProvider;
import com.aiworkspace.videos.models.VideoAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    @Autowired
    private InMemoryKnowledgeRepository inMemoryKnowledgeRepository;

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
        assertFalse(error.has("code"));
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
        JsonNode uploadResponse = OBJECT_MAPPER.readTree(uploaded.body());

        assertEquals(HttpStatus.OK.value(), uploaded.statusCode());
        assertEquals(file.path("id").asText(), uploadResponse.path("sourceId").asText());
        assertEquals("text/plain", uploadResponse.path("detectedContentType").asText());
        assertEquals(1, uploadResponse.path("blockCount").asInt());
        assertFalse(uploadResponse.path("extractedAt").asText().isBlank());
        assertEquals("ai-workspace-document-structure-v1/tika-3.2.3",
                uploadResponse.path("parserVersion").asText());
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
        KnowledgeItem knowledgeItem = knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).get(0);
        assertEquals(file.path("id").asText(), knowledgeItem.sourceId());
        assertEquals(uploadResponse.path("extractedAt").asText(), knowledgeItem.extractedAt().toString());
    }

    @Test
    void storesLargeDocumentsAsOrderedSearchChunks() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Chunked search").body())
                .path("id").asText();
        String content = "Revenue increased during the quarter. ".repeat(6);

        HttpResponse<String> uploaded = uploadDocument(
                owner,
                workspaceId,
                "report.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );
        String sourceId = OBJECT_MAPPER.readTree(uploaded.body()).path("sourceId").asText();
        List<KnowledgeItem> items = knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId);

        assertEquals(HttpStatus.OK.value(), uploaded.statusCode(), uploaded.body());
        assertTrue(items.size() > 1);
        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            assertEquals(sourceId, item.sourceId());
            assertEquals(sourceId + ":" + (index + 1), item.chunkId());
            assertEquals(index + 1, item.chunkSequence());
            assertTrue(item.content().endsWith("."));
            assertEquals("0", item.sectionId());
        }
    }

    @Test
    void deletingDocumentFileRemovesItsKnowledgeChunks() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Deletion lifecycle").body())
                .path("id").asText();
        HttpResponse<String> uploaded = uploadDocument(
                owner,
                workspaceId,
                "obsolete.txt",
                "text/plain",
                "Knowledge that must be removed".getBytes(StandardCharsets.UTF_8)
        );
        String fileId = OBJECT_MAPPER.readTree(uploaded.body()).path("sourceId").asText();

        HttpResponse<String> deleted = send(HttpRequest.newBuilder(
                        uri("/api/v1/workspaces/" + workspaceId + "/files/" + fileId)
                )
                .header("Authorization", owner.basicAuthHeader())
                .DELETE()
                .build());
        HttpResponse<String> fileAfterDeletion = send(HttpRequest.newBuilder(
                        uri("/api/v1/workspaces/" + workspaceId + "/files/" + fileId)
                )
                .header("Authorization", owner.basicAuthHeader())
                .GET()
                .build());

        assertEquals(HttpStatus.NO_CONTENT.value(), deleted.statusCode(), deleted.body());
        assertEquals(HttpStatus.NOT_FOUND.value(), fileAfterDeletion.statusCode());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @Test
    void anotherOwnerCannotDeleteDocumentKnowledge() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        TestUser otherUser = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Protected deletion").body())
                .path("id").asText();
        HttpResponse<String> uploaded = uploadDocument(
                owner,
                workspaceId,
                "private.txt",
                "text/plain",
                "Private knowledge".getBytes(StandardCharsets.UTF_8)
        );
        String fileId = OBJECT_MAPPER.readTree(uploaded.body()).path("sourceId").asText();

        HttpResponse<String> deletion = send(HttpRequest.newBuilder(
                        uri("/api/v1/workspaces/" + workspaceId + "/files/" + fileId)
                )
                .header("Authorization", otherUser.basicAuthHeader())
                .DELETE()
                .build());

        assertEquals(HttpStatus.NOT_FOUND.value(), deletion.statusCode());
        assertEquals(1, knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).size());
    }

    @Test
    void deletingWorkspaceRemovesItsFilesAndKnowledge() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Disposable workspace").body())
                .path("id").asText();
        HttpResponse<String> uploaded = uploadDocument(
                owner,
                workspaceId,
                "temporary.txt",
                "text/plain",
                "Temporary knowledge".getBytes(StandardCharsets.UTF_8)
        );
        assertEquals(HttpStatus.OK.value(), uploaded.statusCode(), uploaded.body());

        HttpResponse<String> deleted = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId))
                .header("Authorization", owner.basicAuthHeader())
                .DELETE()
                .build());
        HttpResponse<String> workspaceAfterDeletion = send(
                HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId))
                        .header("Authorization", owner.basicAuthHeader())
                        .GET()
                        .build()
        );

        assertEquals(HttpStatus.NO_CONTENT.value(), deleted.statusCode(), deleted.body());
        assertEquals(HttpStatus.NOT_FOUND.value(), workspaceAfterDeletion.statusCode());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @Test
    void marksDirectUploadFailedWhenKnowledgeIndexingFails() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Direct indexing failure").body())
                .path("id").asText();
        inMemoryKnowledgeRepository.failNextAddFor(workspaceId);

        HttpResponse<String> uploaded = uploadDocument(
                owner,
                workspaceId,
                "valid.txt",
                "text/plain",
                "Valid extracted content".getBytes(StandardCharsets.UTF_8)
        );
        JsonNode error = OBJECT_MAPPER.readTree(uploaded.body());

        assertEquals(HttpStatus.BAD_GATEWAY.value(), uploaded.statusCode(), uploaded.body());
        assertEquals("Simulated knowledge indexing failure", error.path("detail").asText());
        assertFalse(error.has("code"));
        assertEquals("FAILED", workspaceFiles(owner, workspaceId).path(0).path("status").asText());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void returnsDocumentFailureCodesAndPreservesFailedFileState(
            String filename,
            String contentType,
            byte[] content,
            int expectedStatus,
            String expectedCode
    ) throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Invalid document").body())
                .path("id").asText();

        HttpResponse<String> uploaded = uploadDocument(owner, workspaceId, filename, contentType, content);
        JsonNode error = OBJECT_MAPPER.readTree(uploaded.body());

        assertEquals(expectedStatus, uploaded.statusCode(), uploaded.body());
        assertEquals(expectedStatus, error.path("status").asInt());
        assertEquals(expectedCode, error.path("code").asText());
        assertFalse(error.path("detail").asText().isBlank());
        assertEquals("/api/v1/documents/text", error.path("path").asText());
        assertFalse(error.has("trace"));
        assertFalse(error.has("cause"));

        JsonNode files = workspaceFiles(owner, workspaceId);
        if (content.length == 0) {
            assertEquals(0, files.size());
        } else {
            assertEquals(1, files.size());
            assertEquals(filename, files.path(0).path("originalFilename").asText());
            assertEquals("FAILED", files.path(0).path("status").asText());
        }
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    private static Stream<Arguments> invalidDocuments() {
        return Stream.of(
                Arguments.of("empty.txt", "text/plain", new byte[0], 400, "EMPTY_DOCUMENT"),
                Arguments.of("image.txt", "text/plain", unsupportedImage(), 415, "UNSUPPORTED_DOCUMENT_FORMAT"),
                Arguments.of("broken.pdf", "application/pdf", "%PDF-1.7\ninvalid PDF\n%%EOF"
                        .getBytes(StandardCharsets.UTF_8), 422, "CORRUPT_DOCUMENT"),
                Arguments.of("large.txt", "text/plain", "A".repeat(512).getBytes(StandardCharsets.UTF_8),
                        422, "EXTRACTION_LIMIT_EXCEEDED"),
                Arguments.of("blank.txt", "text/plain", " \n\t ".getBytes(StandardCharsets.UTF_8),
                        422, "NO_EXTRACTABLE_TEXT")
        );
    }

    @Test
    void detectsDocumentContentDespiteMisleadingUploadMetadata() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Content detection").body())
                .path("id").asText();

        HttpResponse<String> uploaded = uploadDocument(owner, workspaceId, "report.pdf", "application/pdf",
                "This is a plain text document.".getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.OK.value(), uploaded.statusCode(), uploaded.body());
        assertEquals("PROCESSED", workspaceFiles(owner, workspaceId).path(0).path("status").asText());
    }

    @Test
    void rejectsDocumentUploadsToAnotherOwnersWorkspaceBeforeProcessing() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        TestUser otherUser = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Private documents").body())
                .path("id").asText();

        HttpResponse<String> uploaded = uploadDocument(otherUser, workspaceId, "image.txt", "text/plain",
                unsupportedImage());
        JsonNode error = OBJECT_MAPPER.readTree(uploaded.body());

        assertEquals(HttpStatus.NOT_FOUND.value(), uploaded.statusCode(), uploaded.body());
        assertFalse(error.has("code"));
        assertEquals(0, workspaceFiles(owner, workspaceId).size());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @Test
    void completesDocumentOnlyOrchestrationIngestion() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        HttpResponse<String> created = createWorkspace(owner, "Orchestrator demo");
        String workspaceId = OBJECT_MAPPER.readTree(created.body()).path("id").asText();

        HttpResponse<String> submitted = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/ingestions"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody(
                        "ai-workspace-test",
                        "workspaceId",
                        workspaceId,
                        "document",
                        "sample.txt",
                        "text/plain",
                        "Knowledge from orchestrator."
                ))
                .build());
        JsonNode submission = OBJECT_MAPPER.readTree(submitted.body());

        assertEquals(HttpStatus.ACCEPTED.value(), submitted.statusCode());
        assertEquals("documents", submission.path("submitted").path(0).asText());
        assertEquals("audio", submission.path("skipped").path(0).asText());

        JsonNode job = waitForJobStatus(owner, submission.path("jobId").asText(), "COMPLETED");
        assertEquals("COMPLETED", stepStatus(job, "documents"));
        assertEquals("SKIPPED", stepStatus(job, "audio"));
        assertEquals("SKIPPED", stepStatus(job, "images"));
        assertEquals("SKIPPED", stepStatus(job, "videos"));
    }

    @Test
    void marksAsyncDocumentAndJobFailedWhenKnowledgeIndexingFails() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Async indexing failure").body())
                .path("id").asText();
        inMemoryKnowledgeRepository.failNextAddFor(workspaceId);

        HttpResponse<String> submitted = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/ingestions"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody(
                        "ai-workspace-test",
                        "workspaceId",
                        workspaceId,
                        "document",
                        "valid.txt",
                        "text/plain",
                        "Valid asynchronous content"
                ))
                .build());
        JsonNode submission = OBJECT_MAPPER.readTree(submitted.body());

        assertEquals(HttpStatus.ACCEPTED.value(), submitted.statusCode(), submitted.body());
        JsonNode job = waitForJobStatus(owner, submission.path("jobId").asText(), "FAILED");
        JsonNode documentsStep = step(job, "documents");
        assertEquals("FAILED", documentsStep.path("status").asText());
        assertEquals("Simulated knowledge indexing failure", documentsStep.path("errorMessage").asText());
        assertTrue(documentsStep.path("errorCode").isNull());
        assertEquals(submission.path("sourceIds").path("documents").asText(),
                workspaceFiles(owner, workspaceId).path(0).path("id").asText());
        assertEquals("FAILED", workspaceFiles(owner, workspaceId).path(0).path("status").asText());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @Test
    void rejectsNamedEmptyDocumentsBeforeSubmittingIngestion() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Empty ingestion document").body())
                .path("id").asText();

        HttpResponse<String> submitted = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/ingestions"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody("ai-workspace-test", "workspaceId", workspaceId, "document",
                        "empty.txt", "text/plain", new byte[0]))
                .build());
        JsonNode error = OBJECT_MAPPER.readTree(submitted.body());

        assertEquals(HttpStatus.BAD_REQUEST.value(), submitted.statusCode(), submitted.body());
        assertEquals("EMPTY_DOCUMENT", error.path("code").asText());
        assertEquals("/api/v1/orchestrator/ingestions", error.path("path").asText());
        assertFalse(error.has("jobId"));
        assertEquals(0, workspaceFiles(owner, workspaceId).size());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());
    }

    @Test
    void skipsUnselectedBrowserDocumentInput() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Unselected document input").body())
                .path("id").asText();

        HttpResponse<String> submitted = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/ingestions"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody("ai-workspace-test", "workspaceId", workspaceId, "document",
                        "", "application/octet-stream", new byte[0]))
                .build());
        assertEquals(HttpStatus.ACCEPTED.value(), submitted.statusCode(), submitted.body());
        JsonNode submission = OBJECT_MAPPER.readTree(submitted.body());
        assertEquals(0, submission.path("submitted").size());
        assertEquals(4, submission.path("skipped").size());

        JsonNode job = waitForJobStatus(owner, submission.path("jobId").asText(), "COMPLETED");
        assertEquals("SKIPPED", stepStatus(job, "documents"));
        assertEquals("SKIPPED", stepStatus(job, "audio"));
        assertEquals("SKIPPED", stepStatus(job, "images"));
        assertEquals("SKIPPED", stepStatus(job, "videos"));
        assertEquals(0, workspaceFiles(owner, workspaceId).size());
    }

    @Test
    void exposesDocumentFailureCodeOnFailedIngestionStep() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        TestUser otherUser = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "Failed ingestion").body())
                .path("id").asText();

        HttpResponse<String> submitted = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/ingestions"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody("ai-workspace-test", "workspaceId", workspaceId, "document",
                        "large.txt", "text/plain", "A".repeat(512).getBytes(StandardCharsets.UTF_8)))
                .build());
        assertEquals(HttpStatus.ACCEPTED.value(), submitted.statusCode(), submitted.body());
        String jobId = OBJECT_MAPPER.readTree(submitted.body()).path("jobId").asText();

        JsonNode job = waitForJobStatus(owner, jobId, "FAILED");
        JsonNode documentsStep = step(job, "documents");
        assertEquals("FAILED", documentsStep.path("status").asText());
        assertEquals("EXTRACTION_LIMIT_EXCEEDED", documentsStep.path("errorCode").asText());
        assertFalse(documentsStep.path("errorMessage").asText().isBlank());
        assertEquals("FAILED", workspaceFiles(owner, workspaceId).path(0).path("status").asText());
        assertTrue(knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId).isEmpty());

        HttpResponse<String> otherUserJob = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/jobs/" + jobId))
                .header("Authorization", otherUser.basicAuthHeader())
                .GET()
                .build());
        assertEquals(HttpStatus.NOT_FOUND.value(), otherUserJob.statusCode());
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

    @Test
    void ingestsPublicYouTubeVideoIntoWorkspaceKnowledge() throws IOException, InterruptedException {
        TestUser owner = registerUser();
        String workspaceId = OBJECT_MAPPER.readTree(createWorkspace(owner, "YouTube workspace").body())
                .path("id").asText();

        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/videos/youtube"))
                .header("Authorization", owner.basicAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"workspaceId":"%s","url":"https://youtu.be/9hE5-98ZeCg?t=10"}
                        """.formatted(workspaceId)))
                .build());
        JsonNode body = OBJECT_MAPPER.readTree(response.body());

        assertEquals(HttpStatus.OK.value(), response.statusCode(), response.body());
        assertEquals("9hE5-98ZeCg", body.path("videoId").asText());
        assertEquals("https://www.youtube.com/watch?v=9hE5-98ZeCg", body.path("url").asText());
        assertEquals("A presenter explains workspace search.", body.path("description").asText());
        assertEquals(1, body.path("segments").size());

        List<KnowledgeItem> items = knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId);
        assertEquals(1, items.size());
        assertEquals(KnowledgeSourceType.VIDEO, items.getFirst().sourceType());
        assertEquals(body.path("sourceId").asText(), items.getFirst().sourceId());
        assertEquals(body.path("url").asText(), items.getFirst().sourceUrl());
        assertTrue(items.getFirst().content().contains("[00:00:01.000 - 00:00:03.000] Presenter:"));
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

    private HttpResponse<String> uploadDocument(
            TestUser user,
            String workspaceId,
            String filename,
            String contentType,
            byte[] content
    ) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/v1/documents/text"))
                .header("Authorization", user.basicAuthHeader())
                .header("Content-Type", "multipart/form-data; boundary=ai-workspace-test")
                .POST(multipartBody("ai-workspace-test", "workspaceId", workspaceId, "file",
                        filename, contentType, content))
                .build());
    }

    private JsonNode workspaceFiles(TestUser user, String workspaceId) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/workspaces/" + workspaceId + "/files"))
                .header("Authorization", user.basicAuthHeader())
                .GET()
                .build());
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static byte[] unsupportedImage() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};
    }

    private JsonNode waitForJobStatus(TestUser user, String jobId, String expectedStatus)
            throws IOException, InterruptedException {
        JsonNode lastJob = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/orchestrator/jobs/" + jobId))
                    .header("Authorization", user.basicAuthHeader())
                    .GET()
                    .build());
            assertEquals(HttpStatus.OK.value(), response.statusCode());
            lastJob = OBJECT_MAPPER.readTree(response.body());
            if (expectedStatus.equals(lastJob.path("status").asText())) {
                return lastJob;
            }

            Thread.sleep(50);
        }

        assertNotNull(lastJob);
        assertEquals(expectedStatus, lastJob.path("status").asText());
        return lastJob;
    }

    private String stepStatus(JsonNode job, String type) {
        return step(job, type).path("status").asText();
    }

    private JsonNode step(JsonNode job, String type) {
        for (JsonNode step : job.path("steps")) {
            if (type.equals(step.path("type").asText())) {
                return step;
            }
        }

        throw new AssertionError("Missing ingestion step " + type);
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
        return multipartBody(boundary, textName, textValue, fileName, originalFilename, contentType,
                fileContent.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.BodyPublisher multipartBody(
            String boundary,
            String textName,
            String textValue,
            String fileName,
            String originalFilename,
            String contentType,
            byte[] fileContent
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
                fileContent,
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

    @TestConfiguration
    static class ApiIntegrationTestConfig {

        @Bean
        @Primary
        InMemoryKnowledgeRepository knowledgeRepository() {
            return new InMemoryKnowledgeRepository();
        }

        @Bean
        @Primary
        KnowledgeAnswerProvider knowledgeAnswerProvider() {
            return (question, context) -> "Test answer";
        }

        @Bean
        @Primary
        TextEmbeddingProvider textEmbeddingProvider() {
            return new TextEmbeddingProvider() {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public String model() {
                    return "test-local-embedding";
                }

                @Override
                public int dimensions() {
                    return 768;
                }

                @Override
                public List<List<Float>> embedDocuments(List<String> texts) {
                    return texts.stream().map(ignored -> testEmbedding()).toList();
                }

                @Override
                public List<Float> embedQuery(String text) {
                    return testEmbedding();
                }

                private List<Float> testEmbedding() {
                    List<Float> vector = new java.util.ArrayList<>(768);
                    for (int index = 0; index < 768; index++) {
                        vector.add(index == 0 ? 1.0f : 0.0f);
                    }
                    return List.copyOf(vector);
                }
            };
        }

        @Bean
        @Primary
        YouTubeVideoUnderstandingProvider youTubeVideoUnderstandingProvider() {
            return (url, prompt) -> new VideoAnalysis(
                    "A presenter explains workspace search.",
                    "Search finds relevant workspace evidence.",
                    "en",
                    List.of(new TranscriptSegment(
                            1_000,
                            3_000,
                            "Presenter",
                            "Search finds relevant workspace evidence."
                    ))
            );
        }
    }

    private static class InMemoryKnowledgeRepository implements KnowledgeRepository {

        private final List<KnowledgeItem> items = new CopyOnWriteArrayList<>();
        private final Set<String> failingWorkspaceIds = ConcurrentHashMap.newKeySet();

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            List<KnowledgeItem> workspaceItems = findKnowledgeItemsByWorkspaceId(workspaceId);
            if (workspaceItems.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new WorkspaceKnowledge(
                    workspaceId,
                    joinedContent(workspaceItems, KnowledgeSourceType.DOCUMENT),
                    joinedContent(workspaceItems, KnowledgeSourceType.AUDIO),
                    joinedContent(workspaceItems, KnowledgeSourceType.VIDEO),
                    joinedContent(workspaceItems, KnowledgeSourceType.IMAGE)
            ));
        }

        @Override
        public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) {
            return items.stream()
                    .filter(item -> item.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) {
            return findKnowledgeItemsByWorkspaceId(workspaceId).stream()
                    .filter(item -> item.content().contains(query))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void addKnowledgeItem(KnowledgeItem item) throws IOException {
            if (failingWorkspaceIds.remove(item.workspaceId())) {
                throw new IOException("Simulated knowledge indexing failure");
            }
            items.add(item);
        }

        @Override
        public void replaceKnowledgeItems(String workspaceId, String sourceId, List<KnowledgeItem> replacements)
                throws IOException {
            if (failingWorkspaceIds.remove(workspaceId)) {
                throw new IOException("Simulated knowledge indexing failure");
            }
            items.removeIf(item -> item.workspaceId().equals(workspaceId) && sourceId.equals(item.sourceId()));
            items.addAll(replacements);
        }

        @Override
        public void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId) && sourceId.equals(item.sourceId()));
        }

        @Override
        public void deleteKnowledgeItemsByWorkspaceId(String workspaceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId));
        }

        void failNextAddFor(String workspaceId) {
            failingWorkspaceIds.add(workspaceId);
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
                throws IOException {
            addKnowledgeItem(new KnowledgeItem(
                    UUID.randomUUID().toString(),
                    workspaceId,
                    sourceTypeFrom(field),
                    field.fieldName(),
                    null,
                    value,
                    Instant.now()
            ));
        }

        private String joinedContent(List<KnowledgeItem> workspaceItems, KnowledgeSourceType sourceType) {
            return workspaceItems.stream()
                    .filter(item -> item.sourceType() == sourceType)
                    .map(KnowledgeItem::content)
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse(null);
        }

        private KnowledgeSourceType sourceTypeFrom(WorkspaceKnowledgeField field) {
            return switch (field) {
                case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
                case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
                case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
                case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
            };
        }
    }
}
