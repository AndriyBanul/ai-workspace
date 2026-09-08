package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiTextEmbeddingClient implements TextEmbeddingProvider {

    private final URI baseUri;
    private final String apiKey;
    private final KnowledgeEmbeddingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiTextEmbeddingClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            KnowledgeEmbeddingProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this(URI.create(stripTrailingSlash(baseUrl)), apiKey, properties, restClient, objectMapper);
    }

    GeminiTextEmbeddingClient(
            URI baseUri,
            String apiKey,
            KnowledgeEmbeddingProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return properties.enabled() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    @Override
    public List<List<Float>> embedDocuments(List<String> texts) throws IOException {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<Float>> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.batchSize()) {
            int end = Math.min(start + properties.batchSize(), texts.size());
            embeddings.addAll(embed(texts.subList(start, end), "RETRIEVAL_DOCUMENT"));
        }
        return List.copyOf(embeddings);
    }

    @Override
    public List<Float> embedQuery(String text) throws IOException {
        return embed(List.of(text), "RETRIEVAL_QUERY").getFirst();
    }

    private List<List<Float>> embed(List<String> texts, String taskType) throws IOException {
        if (!isConfigured()) {
            throw new UpstreamServiceException("Gemini", "Gemini embedding provider is not configured");
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(embeddingUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(texts, taskType))
                    .retrieve()
                    .toEntity(String.class);
            return embeddingsFrom(response.getBody(), texts.size());
        } catch (RestClientResponseException exception) {
            throw new UpstreamServiceException(
                    "Gemini",
                    "Gemini embeddings returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Gemini", "Gemini embeddings are unavailable", exception);
        }
    }

    private String requestBody(List<String> texts, String taskType) throws IOException {
        List<Map<String, Object>> requests = texts.stream().map(text -> {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", "models/" + properties.model());
            request.put("taskType", taskType);
            request.put("content", Map.of("parts", List.of(Map.of("text", text))));
            request.put("outputDimensionality", properties.dimensions());
            return request;
        }).toList();
        return objectMapper.writeValueAsString(Map.of("requests", requests));
    }

    private List<List<Float>> embeddingsFrom(String responseBody, int expectedCount) {
        JsonNode embeddings;
        try {
            embeddings = objectMapper.readTree(responseBody == null ? "{}" : responseBody).path("embeddings");
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
        if (!embeddings.isArray() || embeddings.size() != expectedCount) {
            throw invalidResponse(null);
        }

        List<List<Float>> result = new ArrayList<>(expectedCount);
        for (JsonNode embedding : embeddings) {
            JsonNode values = embedding.path("values");
            if (!values.isArray() || values.size() != properties.dimensions()) {
                throw invalidResponse(null);
            }
            List<Float> vector = new ArrayList<>(values.size());
            double squaredNorm = 0;
            for (JsonNode value : values) {
                float number = value.floatValue();
                if (!Float.isFinite(number)) {
                    throw invalidResponse(null);
                }
                vector.add(number);
                squaredNorm += number * number;
            }
            if (squaredNorm == 0) {
                throw invalidResponse(null);
            }
            double norm = Math.sqrt(squaredNorm);
            result.add(vector.stream().map(value -> (float) (value / norm)).toList());
        }
        return List.copyOf(result);
    }

    private URI embeddingUri() {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUri + "/models/" + properties.model() + ":batchEmbedContents?key=" + encodedKey);
    }

    private UpstreamServiceException invalidResponse(Exception cause) {
        return new UpstreamServiceException("Gemini", "Gemini returned invalid embeddings", cause);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
