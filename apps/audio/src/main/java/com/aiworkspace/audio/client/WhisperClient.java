package com.aiworkspace.audio.client;

import com.aiworkspace.audio.interfaces.SpeechToTextProvider;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.models.WhisperResponse;
import com.aiworkspace.audio.models.WhisperSegmentResponse;
import com.aiworkspace.audio.models.WhisperTimestampsResponse;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WhisperClient implements SpeechToTextProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WhisperClient(
            @Value("${ai-workspace.whisper.base-url:http://localhost:9000}") String baseUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public TranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException {
        String boundary = "ai-workspace-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(buildTranscriptionUri())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, filename, fileContent)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new UpstreamServiceException("Whisper", "Whisper is unavailable", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new UpstreamServiceException("Whisper", "Whisper returned HTTP " + response.statusCode());
        }

        return parseTranscription(response.body());
    }

    private URI buildTranscriptionUri() {
        return URI.create(baseUri + "/asr?task=transcribe&output=json&word_timestamps=true");
    }

    private byte[] buildMultipartBody(
            String boundary,
            String filename,
            byte[] fileContent
    ) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"audio_file\"; filename=\""
                + escapeMultipartFilename(filename) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileContent);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private String escapeMultipartFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio";
        }

        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    TranscriptionResponse parseTranscription(String responseBody) {
        WhisperResponse whisperResponse;
        try {
            whisperResponse = objectMapper.readValue(
                    responseBody == null ? "" : responseBody,
                    WhisperResponse.class
            );
        } catch (IOException exception) {
            throw new UpstreamServiceException("Whisper", "Whisper returned an invalid response", exception);
        }
        if (whisperResponse == null) {
            throw new UpstreamServiceException("Whisper", "Whisper returned an invalid response");
        }

        return new TranscriptionResponse(
                whisperResponse.text() == null ? "" : whisperResponse.text(),
                whisperResponse.language() == null ? "" : whisperResponse.language(),
                parseSegments(whisperResponse.segments())
        );
    }

    private List<TranscriptSegment> parseSegments(List<WhisperSegmentResponse> whisperSegments) {
        if (whisperSegments == null) {
            return List.of();
        }

        List<TranscriptSegment> segments = new ArrayList<>();
        for (WhisperSegmentResponse segment : whisperSegments) {
            if (segment == null) {
                continue;
            }
            String text = readFirstNonBlankText(segment.text(), segment.transcript()).trim();
            if (text.isEmpty()) {
                continue;
            }
            WhisperTimestampsResponse timestamps = segment.timestamps();
            long start = readTimestampMilliseconds(segment.start(), timestamps == null ? null : timestamps.from());
            long end = Math.max(start, readTimestampMilliseconds(
                    segment.end(), timestamps == null ? null : timestamps.to()
            ));
            String speaker = readNormalizedOptionalText(segment.speaker());
            segments.add(new TranscriptSegment(start, end, speaker, text));
        }
        return List.copyOf(segments);
    }

    private long readTimestampMilliseconds(String directTimestamp, String nestedTimestamp) {
        return parseTimestamp(directTimestamp == null ? nestedTimestamp : directTimestamp);
    }

    private long parseTimestamp(String value) {
        if (value == null) {
            return 0L;
        }
        String[] parts = value.trim().split(":");
        try {
            double seconds = 0;
            for (String part : parts) {
                seconds = seconds * 60 + Double.parseDouble(part);
            }
            return Math.max(0L, Math.round(seconds * 1000));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String readFirstNonBlankText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String readNormalizedOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
