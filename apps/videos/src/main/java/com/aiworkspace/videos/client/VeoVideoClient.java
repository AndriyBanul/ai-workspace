package com.aiworkspace.videos.client;

import com.aiworkspace.videos.models.GeneratedVideo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class VeoVideoClient {

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final long POLL_INTERVAL_MILLIS = 5_000L;

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final String aspectRatio;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public VeoVideoClient(
            @Value("${ai-workspace.veo.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.veo.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${ai-workspace.veo.model:veo-3.0-generate-preview}") String model,
            @Value("${ai-workspace.veo.aspect-ratio:16:9}") String aspectRatio,
            RestClient restClient
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiKey,
                model,
                aspectRatio,
                restClient,
                new ObjectMapper()
        );
    }

    VeoVideoClient(
            URI baseUri,
            String apiKey,
            String model,
            String aspectRatio,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.model = model;
        this.aspectRatio = aspectRatio;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedVideo generate(String description) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Veo API key is not configured");
        }

        String operationName = startGeneration(description);
        JsonNode operation = waitForOperation(operationName);

        GeneratedVideo inlineVideo = inlineVideo(operation);
        if (inlineVideo != null) {
            return inlineVideo;
        }

        String videoUri = videoUri(operation);
        if (videoUri == null || videoUri.isBlank()) {
            throw new IOException("Veo operation completed without a generated video URI");
        }

        return downloadVideo(videoUri);
    }

    private String startGeneration(String description) throws IOException, InterruptedException {
        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .uri(generationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(generationRequestBody(description))
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException exception) {
            throw new IOException(
                    "Veo returned HTTP " + exception.getStatusCode().value() + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        }

        JsonNode responseJson = objectMapper.readTree(response.getBody() == null ? "" : response.getBody());
        String operationName = responseJson.path("name").asText();
        if (operationName == null || operationName.isBlank()) {
            throw new IOException("Veo did not return an operation name");
        }

        return operationName;
    }

    private JsonNode waitForOperation(String operationName) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            ResponseEntity<String> response;
            try {
                response = restClient.get()
                        .uri(operationUri(operationName))
                        .retrieve()
                        .toEntity(String.class);
            } catch (RestClientResponseException exception) {
                throw new IOException(
                        "Veo operation returned HTTP " + exception.getStatusCode().value() + ": "
                                + exception.getResponseBodyAsString(),
                        exception
                );
            }

            JsonNode operation = objectMapper.readTree(response.getBody() == null ? "" : response.getBody());
            if (operation.path("done").asBoolean(false)) {
                JsonNode error = operation.get("error");
                if (error != null && !error.isNull()) {
                    throw new IOException("Veo operation failed: " + error);
                }

                return operation;
            }

            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        throw new IOException("Timed out while waiting for Veo video generation");
    }

    private GeneratedVideo downloadVideo(String videoUri) throws IOException, InterruptedException {
        ResponseEntity<byte[]> response;
        try {
            response = restClient.get()
                    .uri(downloadUri(videoUri))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException exception) {
            throw new IOException(
                    "Veo video download returned HTTP " + exception.getStatusCode().value() + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        }

        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
        String mediaType = response.getHeaders().getContentType() == null
                ? "video/mp4"
                : response.getHeaders().getContentType().toString().split(";", 2)[0];

        if (!mediaType.startsWith("video/")) {
            throw new IOException("Veo did not return video content");
        }

        return new GeneratedVideo(filename(mediaType), mediaType, body);
    }

    private URI generationUri() {
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUri + "/models/" + model + ":predictLongRunning?key=" + encodedApiKey);
    }

    private URI operationUri(String operationName) {
        if (operationName.startsWith("http://") || operationName.startsWith("https://")) {
            return withApiKey(operationName);
        }

        return withApiKey(baseUri + "/" + operationName);
    }

    private URI downloadUri(String videoUri) {
        return withApiKey(videoUri);
    }

    private URI withApiKey(String uri) {
        if (uri.contains("key=")) {
            return URI.create(uri);
        }

        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String separator = uri.contains("?") ? "&" : "?";
        return URI.create(uri + separator + "key=" + encodedApiKey);
    }

    private String generationRequestBody(String description) throws IOException {
        Map<String, Object> instance = Map.of("prompt", description);
        Map<String, Object> parameters = Map.of("aspectRatio", aspectRatio);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instances", List.of(instance));
        body.put("parameters", parameters);

        return objectMapper.writeValueAsString(body);
    }

    private GeneratedVideo inlineVideo(JsonNode operation) throws IOException {
        JsonNode video = firstVideo(operation);
        if (video == null) {
            return null;
        }

        JsonNode bytes = firstPresent(video, "bytesBase64Encoded", "bytes_base64_encoded", "data");
        if (bytes == null || bytes.asText().isBlank()) {
            return null;
        }

        String mediaType = textValue(video, "mimeType", "mime_type");
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = "video/mp4";
        }

        return new GeneratedVideo(filename(mediaType), mediaType, Base64.getDecoder().decode(bytes.asText()));
    }

    private String videoUri(JsonNode operation) {
        JsonNode video = firstVideo(operation);
        if (video == null) {
            return "";
        }

        String uri = textValue(video, "uri");
        if (uri != null && !uri.isBlank()) {
            return uri;
        }

        return textValue(video, "url");
    }

    private JsonNode firstVideo(JsonNode operation) {
        List<JsonNode> candidates = List.of(
                operation.path("response").path("generatedVideos").path(0).path("video"),
                operation.path("response").path("generatedSamples").path(0).path("video"),
                operation.path("response").path("generateVideoResponse").path("generatedVideos").path(0).path("video"),
                operation.path("response").path("generateVideoResponse").path("generatedSamples").path(0).path("video"),
                operation.path("response").path("videos").path(0),
                operation.path("response").path("video")
        );

        for (JsonNode candidate : candidates) {
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }

        return null;
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }

        return null;
    }

    private String textValue(JsonNode node, String... fieldNames) {
        JsonNode value = firstPresent(node, fieldNames);
        return value == null ? "" : value.asText();
    }

    private String filename(String mediaType) {
        if ("video/webm".equals(mediaType)) {
            return "generated-video.webm";
        }

        return "generated-video.mp4";
    }
}
