package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.providers.SpeechToTextProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WhisperClient implements SpeechToTextProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WhisperClient(
            @Value("${ai-workspace.whisper.base-url:http://localhost:9000}") String baseUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                restClient,
                objectMapper
        );
    }

    WhisperClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public TranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException {
        String boundary = "ai-workspace-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(transcriptionUri())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, filename, fileContent)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Whisper returned HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        JsonNode responseJson = objectMapper.readTree(response.body() == null ? "" : response.body());
        return new TranscriptionResponse(
                textValue(responseJson, "text"),
                textValue(responseJson, "language")
        );
    }

    private URI transcriptionUri() {
        return URI.create(baseUri + "/asr?task=transcribe&output=json");
    }

    private byte[] multipartBody(
            String boundary,
            String filename,
            byte[] fileContent
    ) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"audio_file\"; filename=\""
                + safeFilename(filename) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileContent);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio";
        }

        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
