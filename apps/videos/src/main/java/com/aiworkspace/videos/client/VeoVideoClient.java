package com.aiworkspace.videos.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.videos.interfaces.VideoGenerationProvider;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VeoGeneratedVideoResponse;
import com.aiworkspace.videos.models.VeoGenerationResponse;
import com.aiworkspace.videos.models.VeoOperationResponse;
import com.aiworkspace.videos.models.VeoVideoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class VeoVideoClient implements VideoGenerationProvider {

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final long POLL_INTERVAL_MILLIS = 5_000L;

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final String aspectRatio;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VeoVideoClient(
            @Value("${ai-workspace.veo.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.veo.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${ai-workspace.veo.model:veo-3.0-generate-preview}") String model,
            @Value("${ai-workspace.veo.aspect-ratio:16:9}") String aspectRatio,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.aspectRatio = aspectRatio;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedVideo generate(String description) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamServiceException("Veo", "Veo API key is not configured");
        }

        String operationName = startGeneration(description);
        VeoOperationResponse operation = waitForOperation(operationName);

        GeneratedVideo inlineVideo = decodeInlineVideo(operation);
        if (inlineVideo != null) {
            return inlineVideo;
        }

        String videoUri = extractVideoUri(operation);
        if (videoUri == null || videoUri.isBlank()) {
            throw new UpstreamServiceException("Veo", "Veo operation completed without a generated video URI");
        }

        return downloadVideo(videoUri);
    }

    private String startGeneration(String description) throws IOException, InterruptedException {
        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .uri(buildGenerationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildGenerationRequestBody(description))
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException exception) {
            throw new UpstreamServiceException(
                    "Veo",
                    "Veo returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new UpstreamServiceException(
                    "Veo",
                    "Veo is unavailable",
                    exception
            );
        }

        VeoOperationResponse operation = parseResponse(
                response.getBody(),
                "Veo returned an invalid generation response"
        );
        String operationName = operation.name();
        if (operationName == null || operationName.isBlank()) {
            throw new UpstreamServiceException("Veo", "Veo did not return an operation name");
        }

        return operationName;
    }

    private VeoOperationResponse waitForOperation(String operationName) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            ResponseEntity<String> response;
            try {
                response = restClient.get()
                        .uri(buildOperationUri(operationName))
                        .retrieve()
                        .toEntity(String.class);
            } catch (RestClientResponseException exception) {
                throw new UpstreamServiceException(
                        "Veo",
                        "Veo operation returned HTTP " + exception.getStatusCode().value(),
                        exception
                );
            } catch (RestClientException exception) {
                throw new UpstreamServiceException(
                        "Veo",
                        "Veo operation is unavailable",
                        exception
                );
            }

            VeoOperationResponse operation = parseResponse(
                    response.getBody(),
                    "Veo operation returned an invalid response"
            );
            if (operation.done()) {
                if (operation.error() != null) {
                    throw new UpstreamServiceException("Veo", "Veo operation failed");
                }

                return operation;
            }

            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        throw new UpstreamServiceException("Veo", "Timed out while waiting for Veo video generation");
    }

    private GeneratedVideo downloadVideo(String videoUri) throws IOException, InterruptedException {
        ResponseEntity<byte[]> response;
        try {
            response = restClient.get()
                    .uri(buildDownloadUri(videoUri))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException exception) {
            throw new UpstreamServiceException(
                    "Veo",
                    "Veo video download returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new UpstreamServiceException(
                    "Veo",
                    "Veo video download is unavailable",
                    exception
            );
        }

        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
        String mediaType = response.getHeaders().getContentType() == null
                ? "video/mp4"
                : response.getHeaders().getContentType().toString().split(";", 2)[0];

        if (!mediaType.startsWith("video/")) {
            throw new UpstreamServiceException("Veo", "Veo did not return video content");
        }

        return new GeneratedVideo(resolveVideoFilename(mediaType), mediaType, body);
    }

    private URI buildGenerationUri() {
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUri + "/models/" + model + ":predictLongRunning?key=" + encodedApiKey);
    }

    private URI buildOperationUri(String operationName) {
        if (operationName.startsWith("http://") || operationName.startsWith("https://")) {
            return withApiKey(operationName);
        }

        return withApiKey(baseUri + "/" + operationName);
    }

    private URI buildDownloadUri(String videoUri) {
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

    private String buildGenerationRequestBody(String description) throws IOException {
        Map<String, Object> instance = Map.of("prompt", description);
        Map<String, Object> parameters = Map.of("aspectRatio", aspectRatio);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instances", List.of(instance));
        body.put("parameters", parameters);

        return objectMapper.writeValueAsString(body);
    }

    GeneratedVideo decodeInlineVideo(VeoOperationResponse operation) throws IOException {
        VeoVideoResponse video = findFirstVideo(operation);
        if (video == null) {
            return null;
        }

        String bytes = readFirstPresentValue(video.bytesBase64Encoded(), video.snakeCaseBytesBase64Encoded(), video.data());
        if (bytes == null || bytes.isBlank()) {
            return null;
        }

        String mediaType = readFirstPresentValue(video.mimeType(), video.snakeCaseMimeType());
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = "video/mp4";
        }

        try {
            return new GeneratedVideo(resolveVideoFilename(mediaType), mediaType, Base64.getDecoder().decode(bytes));
        } catch (IllegalArgumentException exception) {
            throw new UpstreamServiceException("Veo", "Veo returned invalid inline video content", exception);
        }
    }

    String extractVideoUri(VeoOperationResponse operation) {
        VeoVideoResponse video = findFirstVideo(operation);
        if (video == null) {
            return "";
        }

        String uri = video.uri();
        if (uri != null && !uri.isBlank()) {
            return uri;
        }

        return video.url() == null ? "" : video.url();
    }

    private VeoVideoResponse findFirstVideo(VeoOperationResponse operation) {
        VeoGenerationResponse response = operation.response();
        if (response == null) {
            return null;
        }
        VeoGenerationResponse nested = response.generateVideoResponse();
        VeoVideoResponse[] candidates = {
                findFirstGeneratedVideo(response.generatedVideos()),
                findFirstGeneratedVideo(response.generatedSamples()),
                nested == null ? null : findFirstGeneratedVideo(nested.generatedVideos()),
                nested == null ? null : findFirstGeneratedVideo(nested.generatedSamples()),
                response.videos() == null || response.videos().isEmpty() ? null : response.videos().getFirst(),
                response.video()
        };

        for (VeoVideoResponse candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private VeoVideoResponse findFirstGeneratedVideo(List<VeoGeneratedVideoResponse> videos) {
        if (videos == null || videos.isEmpty() || videos.getFirst() == null) {
            return null;
        }
        return videos.getFirst().video();
    }

    private String readFirstPresentValue(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    VeoOperationResponse parseResponse(String responseBody, String message) {
        try {
            VeoOperationResponse response = objectMapper.readValue(
                    responseBody == null ? "" : responseBody, VeoOperationResponse.class
            );
            if (response == null) {
                throw new UpstreamServiceException("Veo", message);
            }
            return response;
        } catch (IOException exception) {
            throw new UpstreamServiceException("Veo", message, exception);
        }
    }

    private String resolveVideoFilename(String mediaType) {
        if ("video/webm".equals(mediaType)) {
            return "generated-video.webm";
        }

        return "generated-video.mp4";
    }
}
