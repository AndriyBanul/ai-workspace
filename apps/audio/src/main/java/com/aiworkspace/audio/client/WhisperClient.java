package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.WhisperTranscriptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhisperClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WhisperClient(@Value("${ai-workspace.whisper.base-url:http://localhost:9000}") String baseUrl) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper()
        );
    }

    WhisperClient(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public WhisperTranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException {
        String boundary = "AiWorkspaceBoundary" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(transcriptionUri())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(multipartBody(boundary, filename, fileContent)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Whisper returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        return new WhisperTranscriptionResponse(
                textValue(responseJson, "text"),
                textValue(responseJson, "language")
        );
    }

    private URI transcriptionUri() {
        return URI.create(baseUri + "/asr?task=transcribe&output=json");
    }

    private List<byte[]> multipartBody(String boundary, String filename, byte[] fileContent) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio_file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        return List.of(
                header.getBytes(StandardCharsets.UTF_8),
                fileContent,
                footer.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
