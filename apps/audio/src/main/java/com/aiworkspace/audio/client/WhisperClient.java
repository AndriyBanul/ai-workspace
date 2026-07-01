package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.WhisperTranscriptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WhisperClient {

    private final URI baseUri;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WhisperClient(
            @Value("${ai-workspace.whisper.base-url:http://localhost:9000}") String baseUrl,
            RestClient restClient
    ) {
        this(
                URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl),
                restClient,
                new ObjectMapper()
        );
    }

    WhisperClient(URI baseUri, RestClient restClient, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WhisperTranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(transcriptionUri())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(filename, fileContent))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody() == null ? "" : response.getBody());
            return new WhisperTranscriptionResponse(
                    textValue(responseJson, "text"),
                    textValue(responseJson, "language")
            );
        } catch (RestClientResponseException exception) {
            throw new IOException(
                    "Whisper returned HTTP " + exception.getStatusCode().value() + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        }
    }

    private URI transcriptionUri() {
        return URI.create(baseUri + "/asr?task=transcribe&output=json");
    }

    private MultiValueMap<String, Object> multipartBody(String filename, byte[] fileContent) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio_file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        return body;
    }

    private String textValue(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
