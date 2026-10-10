package com.aiworkspace.videos.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.aiworkspace.shared.models.GeminiCandidateResponse;
import com.aiworkspace.shared.models.GeminiPartResponse;
import com.aiworkspace.shared.models.GeminiResponse;
import com.aiworkspace.videos.interfaces.VideoUnderstandingProvider;
import com.aiworkspace.videos.interfaces.YouTubeVideoUnderstandingProvider;
import com.aiworkspace.videos.models.GeminiVideoAnalysisResponse;
import com.aiworkspace.videos.models.GeminiVideoSegmentResponse;
import com.aiworkspace.videos.models.VideoAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
public class GeminiVideoClient implements VideoUnderstandingProvider, YouTubeVideoUnderstandingProvider {

    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiVideoClient(
            @Value("${ai-workspace.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${ai-workspace.gemini.api-key:}") String apiKey,
            @Value("${ai-workspace.gemini.video-model:gemini-2.5-flash}") String model,
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
    public VideoAnalysis analyze(byte[] videoContent, String mimeType, String prompt)
            throws IOException, InterruptedException {
        return analyzeRequest(buildRequestBody(videoContent, mimeType, prompt));
    }

    @Override
    public VideoAnalysis analyzeYouTube(String youtubeUrl, String prompt) throws IOException, InterruptedException {
        return analyzeRequest(buildYouTubeRequestBody(youtubeUrl, prompt));
    }

    private VideoAnalysis analyzeRequest(String requestBody) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamServiceException("Gemini", "Gemini API key is not configured");
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(buildGenerationUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            return parseAnalysis(response.getBody() == null ? "" : response.getBody());
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

    String buildRequestBody(byte[] videoContent, String mimeType, String prompt) throws IOException {
        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", Base64.getEncoder().encodeToString(videoContent));

        Map<String, Object> textPart = Map.of("text", buildStructuredPrompt(prompt));
        Map<String, Object> videoPart = Map.of("inline_data", inlineData);
        return buildRequestBody(List.of(textPart, videoPart));
    }

    String buildYouTubeRequestBody(String youtubeUrl, String prompt) throws IOException {
        Map<String, Object> fileData = Map.of("file_uri", youtubeUrl);
        Map<String, Object> videoPart = Map.of("file_data", fileData);
        Map<String, Object> textPart = Map.of("text", buildStructuredPrompt(prompt));
        return buildRequestBody(List.of(videoPart, textPart));
    }

    private String buildRequestBody(List<Map<String, Object>> parts) throws IOException {
        Map<String, Object> content = Map.of("parts", parts);
        Map<String, Object> generationConfig = Map.of(
                "temperature", 0.1,
                "responseMimeType", "application/json",
                "responseSchema", buildResponseSchema()
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(body);
    }

    private String buildStructuredPrompt(String prompt) {
        return prompt + "\n\n"
                + "Return a visual summary and a verbatim spoken transcript. Include timed transcript segments "
                + "in milliseconds. Use an empty transcript and empty segments when there is no speech. "
                + "Do not infer words that are not audible. Speaker labels are optional.";
    }

    VideoAnalysis parseAnalysis(String responseBody) {
        GeminiResponse response = parseResponse(responseBody, GeminiResponse.class);
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new UpstreamServiceException("Gemini", "Gemini did not return any candidates");
        }

        GeminiCandidateResponse candidate = response.candidates().getFirst();
        if (candidate == null || candidate.content() == null || candidate.content().parts() == null) {
            throw new UpstreamServiceException("Gemini", "Gemini did not return response content");
        }

        StringBuilder content = new StringBuilder();
        for (GeminiPartResponse part : candidate.content().parts()) {
            if (part != null && part.text() != null) {
                content.append(part.text());
            }
        }

        String result = content.toString().trim();
        if (result.isEmpty()) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an empty video description");
        }
        GeminiVideoAnalysisResponse analysis = parseResponse(result, GeminiVideoAnalysisResponse.class);
        if (analysis == null) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an invalid response");
        }
        String summary = analysis.summary() == null ? "" : analysis.summary().trim();
        if (summary.isEmpty()) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an empty video summary");
        }
        return new VideoAnalysis(
                summary,
                analysis.transcript() == null ? "" : analysis.transcript().trim(),
                readNormalizedOptionalText(analysis.language()),
                parseSegments(analysis.segments())
        );
    }

    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> segment = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "startMilliseconds", Map.of("type", "INTEGER"),
                        "endMilliseconds", Map.of("type", "INTEGER"),
                        "speaker", Map.of("type", "STRING", "nullable", true),
                        "text", Map.of("type", "STRING")
                ),
                "required", List.of("startMilliseconds", "endMilliseconds", "text")
        );
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "summary", Map.of("type", "STRING"),
                        "transcript", Map.of("type", "STRING"),
                        "language", Map.of("type", "STRING", "nullable", true),
                        "segments", Map.of("type", "ARRAY", "items", segment)
                ),
                "required", List.of("summary", "transcript", "segments")
        );
    }

    private List<TranscriptSegment> parseSegments(List<GeminiVideoSegmentResponse> responseSegments) {
        if (responseSegments == null) {
            return List.of();
        }
        List<TranscriptSegment> segments = new ArrayList<>();
        for (GeminiVideoSegmentResponse segment : responseSegments) {
            if (segment == null) {
                continue;
            }
            String text = segment.text() == null ? "" : segment.text().trim();
            if (text.isEmpty()) {
                continue;
            }
            long start = Math.max(0L, segment.startMilliseconds());
            long end = Math.max(start, segment.endMilliseconds());
            segments.add(new TranscriptSegment(start, end, readNormalizedOptionalText(segment.speaker()), text));
        }
        return List.copyOf(segments);
    }

    private String readNormalizedOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T parseResponse(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody == null ? "" : responseBody, responseType);
        } catch (IOException exception) {
            throw new UpstreamServiceException("Gemini", "Gemini returned an invalid response", exception);
        }
    }
}
