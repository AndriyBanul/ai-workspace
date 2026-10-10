package com.aiworkspace.images.client;

import com.aiworkspace.images.interfaces.ImageUnderstandingProvider;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.models.GeminiCandidateResponse;
import com.aiworkspace.shared.models.GeminiPartResponse;
import com.aiworkspace.shared.models.GeminiResponse;
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
public class GeminiImageClient implements ImageUnderstandingProvider {

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiImageClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            @Value("${ai-workspace.gemini.image-model:gemini-2.5-flash}") String model,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String describe(byte[] imageContent, String mimeType, String prompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamServiceException("Gemini", "Gemini API key is not configured");
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(buildGenerationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(imageContent, mimeType, prompt))
                    .retrieve()
                    .toEntity(String.class);

            return parseDescription(response.getBody() == null ? "" : response.getBody());
        } catch (RestClientResponseException exception) {
            throw new UpstreamServiceException(
                    "Gemini",
                    "Gemini returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new UpstreamServiceException(
                    "Gemini",
                    "Gemini is unavailable",
                    exception
            );
        }
    }

    private URI buildGenerationUri() {
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUri + "/models/" + model + ":generateContent?key=" + encodedApiKey);
    }

    private String buildRequestBody(byte[] imageContent, String mimeType, String prompt) throws IOException {
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

    String parseDescription(String responseBody) {
        GeminiResponse response = parseResponse(responseBody);
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new UpstreamServiceException("Gemini", "Gemini did not return any candidates");
        }

        GeminiCandidateResponse candidate = response.candidates().getFirst();
        if (candidate == null || candidate.content() == null || candidate.content().parts() == null) {
            throw new UpstreamServiceException("Gemini", "Gemini did not return response content");
        }

        StringBuilder description = new StringBuilder();
        for (GeminiPartResponse part : candidate.content().parts()) {
            if (part != null && part.text() != null) {
                description.append(part.text());
            }
        }

        String result = description.toString().trim();
        if (result.isEmpty()) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an empty image description");
        }

        return result;
    }

    private GeminiResponse parseResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody == null ? "" : responseBody, GeminiResponse.class);
        } catch (IOException exception) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an invalid response", exception);
        }
    }
}
