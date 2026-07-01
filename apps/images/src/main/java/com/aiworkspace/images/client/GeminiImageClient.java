package com.aiworkspace.images.client;

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
public class GeminiImageClient {

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiImageClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            @Value("${ai-workspace.gemini.image-model:gemini-2.5-flash}") String model,
            RestClient restClient
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiKey,
                model,
                restClient,
                new ObjectMapper()
        );
    }

    GeminiImageClient(
            URI baseUri,
            String apiKey,
            String model,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String describe(byte[] imageContent, String mimeType, String prompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Gemini API key is not configured");
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(generationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(imageContent, mimeType, prompt))
                    .retrieve()
                    .toEntity(String.class);

            return descriptionFrom(response.getBody() == null ? "" : response.getBody());
        } catch (RestClientResponseException exception) {
            throw new IOException(
                    "Gemini returned HTTP " + exception.getStatusCode().value() + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        }
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
