package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.providers.KnowledgeAnswerProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class GeminiKnowledgeAnswerClient implements KnowledgeAnswerProvider {

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiKnowledgeAnswerClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            @Value("${ai-workspace.gemini.text-model:gemini-2.5-flash}") String model,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiKey,
                model,
                restClient,
                objectMapper
        );
    }

    GeminiKnowledgeAnswerClient(
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

    @Override
    public String answer(String question, String context) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Gemini API key is not configured");
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(generationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(question, context))
                    .retrieve()
                    .toEntity(String.class);

            return answerFrom(response.getBody() == null ? "" : response.getBody());
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

    private String requestBody(String question, String context) throws IOException {
        Map<String, Object> textPart = Map.of("text", prompt(question, context));
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> generationConfig = Map.of("temperature", 0.2);

        return objectMapper.writeValueAsString(Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        ));
    }

    private String prompt(String question, String context) {
        return """
                You answer questions using only the workspace context below.
                If the context does not contain enough information, say that the workspace context does not contain enough information.
                Keep the answer concise and practical.
                When making claims, mention the relevant source file names when useful.

                Workspace context:
                %s

                User question:
                %s
                """.formatted(context, question);
    }

    private String answerFrom(String responseBody) throws IOException {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode candidates = responseJson.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("Gemini did not return any candidates");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw new IOException("Gemini did not return response content");
        }

        StringBuilder answer = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && !text.isNull()) {
                answer.append(text.asText());
            }
        }

        String result = answer.toString().trim();
        if (result.isEmpty()) {
            throw new IOException("Gemini returned an empty answer");
        }

        return result;
    }
}
