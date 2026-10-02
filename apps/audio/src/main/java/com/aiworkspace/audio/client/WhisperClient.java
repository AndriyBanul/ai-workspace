package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.interfaces.SpeechToTextProvider;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
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
import java.util.ArrayList;
import java.util.List;
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
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new UpstreamServiceException("Whisper", "Whisper is unavailable", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new UpstreamServiceException("Whisper", "Whisper returned HTTP " + response.statusCode());
        }

        JsonNode responseJson;
        try {
            responseJson = objectMapper.readTree(response.body() == null ? "" : response.body());
        } catch (IOException exception) {
            throw new UpstreamServiceException("Whisper", "Whisper returned an invalid response", exception);
        }

        return transcriptionFrom(responseJson);
    }

    private URI transcriptionUri() {
        return URI.create(baseUri + "/asr?task=transcribe&output=json&word_timestamps=true");
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

    TranscriptionResponse transcriptionFrom(JsonNode responseJson) {
        return new TranscriptionResponse(
                textValue(responseJson, "text"),
                textValue(responseJson, "language"),
                segmentsFrom(responseJson.path("segments"))
        );
    }

    private List<TranscriptSegment> segmentsFrom(JsonNode segmentsJson) {
        if (!segmentsJson.isArray()) {
            return List.of();
        }

        List<TranscriptSegment> segments = new ArrayList<>();
        for (JsonNode segmentJson : segmentsJson) {
            String text = firstText(segmentJson, "text", "transcript").trim();
            if (text.isEmpty()) {
                continue;
            }
            long start = timestampMilliseconds(segmentJson, "start", "from");
            long end = Math.max(start, timestampMilliseconds(segmentJson, "end", "to"));
            String speaker = normalizedOptionalText(segmentJson, "speaker");
            segments.add(new TranscriptSegment(start, end, speaker, text));
        }
        return List.copyOf(segments);
    }

    private long timestampMilliseconds(JsonNode segmentJson, String directField, String nestedField) {
        JsonNode value = segmentJson.get(directField);
        if (value == null || value.isNull()) {
            value = segmentJson.path("timestamps").get(nestedField);
        }
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            return Math.max(0L, Math.round(value.asDouble() * 1000));
        }
        return parseTimestamp(value.asText());
    }

    private long parseTimestamp(String value) {
        String[] parts = value.trim().split(":");
        try {
            double seconds = 0;
            for (String part : parts) {
                seconds = seconds * 60 + Double.parseDouble(part);
            }
            return Math.max(0L, Math.round(seconds * 1000));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String firstText(JsonNode json, String firstField, String secondField) {
        String first = textValue(json, firstField);
        return first.isBlank() ? textValue(json, secondField) : first;
    }

    private String normalizedOptionalText(JsonNode json, String fieldName) {
        String value = textValue(json, fieldName);
        return value.isBlank() ? null : value.trim();
    }
}
