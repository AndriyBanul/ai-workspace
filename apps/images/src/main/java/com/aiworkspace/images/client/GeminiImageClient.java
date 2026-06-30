package com.aiworkspace.images.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiImageClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiImageClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            @Value("${ai-workspace.gemini.image-model:gemini-2.5-flash}") String model
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiKey,
                model,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper()
        );
    }

    GeminiImageClient(
            URI baseUri,
            String apiKey,
            String model,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String describe(byte[] imageContent, String mimeType, String prompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Gemini API key is not configured");
        }

        HttpRequest request = HttpRequest.newBuilder(generationUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(imageContent, mimeType, prompt)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return descriptionFrom(response.body());
    }

    private URI generationUri() {
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUri + "/models/" + model + ":generateContent?key=" + encodedApiKey);
    }

    private String requestBody(byte[] imageContent, String mimeType, String prompt) throws IOException {
        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", Base64.getEncoder().encodeToString(imageContent));

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of("inline_data", inlineData);
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> generationConfig = Map.of("temperature", 0.2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(body);
    }

    private String descriptionFrom(String responseBody) throws IOException {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode candidates = responseJson.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("Gemini did not return any candidates");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw new IOException("Gemini did not return response content");
        }

        StringBuilder description = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && !text.isNull()) {
                description.append(text.asText());
            }
        }

        String result = description.toString().trim();
        if (result.isEmpty()) {
            throw new IOException("Gemini returned an empty image description");
        }

        return result;
    }
}
