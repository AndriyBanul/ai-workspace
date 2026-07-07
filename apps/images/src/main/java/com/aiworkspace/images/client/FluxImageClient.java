package com.aiworkspace.images.client;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.providers.ImageGenerationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FluxImageClient implements ImageGenerationProvider {

    private final URI baseUri;
    private final String apiToken;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FluxImageClient(
            @Value("${ai-workspace.flux.base-url:https://api-inference.huggingface.co/models}") String baseUrl,
            @Value("${ai-workspace.flux.api-token:}") String apiToken,
            @Value("${ai-workspace.flux.model:black-forest-labs/FLUX.1-dev}") String model,
            RestClient restClient
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                apiToken,
                model,
                restClient,
                new ObjectMapper()
        );
    }

    FluxImageClient(
            URI baseUri,
            String apiToken,
            String model,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = baseUri;
        this.apiToken = apiToken;
        this.model = model;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IOException("FLUX API token is not configured");
        }

        ResponseEntity<byte[]> response;
        try {
            response = restClient.post()
                    .uri(generationUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(description))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientResponseException exception) {
            throw new IOException(
                    "FLUX returned HTTP " + exception.getStatusCode().value() + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        }

        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
        String mediaType = response.getHeaders().getContentType() == null
                ? ""
                : response.getHeaders().getContentType().toString().split(";", 2)[0];
        if (!mediaType.startsWith("image/")) {
            throw new IOException("FLUX did not return image content: " + textBody(body));
        }

        return new GeneratedImage(filename(mediaType), mediaType, body);
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
