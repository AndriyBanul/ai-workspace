package com.aiworkspace.images.client;

import com.aiworkspace.images.models.GeneratedImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FluxImageClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final URI baseUri;
    private final String apiToken;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FluxImageClient(
            @Value("${ai-workspace.flux.base-url:https://api-inference.huggingface.co/models}") String baseUrl,
            @Value("${ai-workspace.flux.api-token:}") String apiToken,
            @Value("${ai-workspace.flux.model:black-forest-labs/FLUX.1-dev}") String model
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiToken,
                model,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper()
        );
    }

    FluxImageClient(
            URI baseUri,
            String apiToken,
            String model,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiToken = apiToken;
        this.model = model;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IOException("FLUX API token is not configured");
        }

        HttpRequest request = HttpRequest.newBuilder(generationUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(description)))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("FLUX returned HTTP " + response.statusCode() + ": " + textBody(response.body()));
        }

        String mediaType = response.headers()
                .firstValue("Content-Type")
                .map(value -> value.split(";", 2)[0])
                .orElse("");
        if (!mediaType.startsWith("image/")) {
            throw new IOException("FLUX did not return image content: " + textBody(response.body()));
        }

        return new GeneratedImage(filename(mediaType), mediaType, response.body());
    }

    private URI generationUri() {
        return URI.create(baseUri + "/" + model);
    }

    private String requestBody(String description) throws IOException {
        Map<String, Object> options = Map.of("wait_for_model", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", description);
        body.put("options", options);

        return objectMapper.writeValueAsString(body);
    }

    private String filename(String mediaType) {
        if ("image/jpeg".equals(mediaType)) {
            return "generated-image.jpg";
        }

        if ("image/webp".equals(mediaType)) {
            return "generated-image.webp";
        }

        return "generated-image.png";
    }

    private String textBody(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }
}
