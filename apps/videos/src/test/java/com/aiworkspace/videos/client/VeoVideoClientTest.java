package com.aiworkspace.videos.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.videos.models.VeoOperationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeoVideoClientTest {

    private final VeoVideoClient client = new VeoVideoClient(
            "http://localhost", "test-key", "test-model", "16:9", RestClient.builder().build(), new ObjectMapper()
    );

    @Test
    void extractsUrisFromAllSupportedResponseLayouts() {
        String video = "{\"uri\":\"https://example.test/video.mp4\",\"extra\":true}";
        for (String response : List.of(
                "{\"generatedVideos\":[{\"video\":" + video + "}]}",
                "{\"generatedSamples\":[{\"video\":" + video + "}]}",
                "{\"generateVideoResponse\":{\"generatedVideos\":[{\"video\":" + video + "}]}}",
                "{\"generateVideoResponse\":{\"generatedSamples\":[{\"video\":" + video + "}]}}",
                "{\"videos\":[" + video + "]}", "{\"video\":" + video + "}"
        )) {
            var operation = parseOperation(response);
            assertEquals("https://example.test/video.mp4", client.extractVideoUri(operation), response);
        }
    }

    @Test
    void preservesVideoLayoutAndInlineFieldPrecedence() throws Exception {
        var operation = parseOperation("""
                {"generatedVideos":[{"video":{
                  "bytesBase64Encoded":"AQI=","bytes_base64_encoded":"Aw==","data":"invalid",
                  "mimeType":"video/mp4","mime_type":"video/webm","uri":"first"
                }}],"generatedSamples":[{"video":{"uri":"second"}}],"video":{"uri":"third"}}
                """);

        var video = client.decodeInlineVideo(operation);

        assertArrayEquals(new byte[]{1, 2}, video.content());
        assertEquals("video/mp4", video.mediaType());
        assertEquals("first", client.extractVideoUri(operation));
    }

    @Test
    void supportsSnakeCaseInlineFieldsAndUrlFallback() throws Exception {
        var operation = parseOperation("""
                {"video":{"bytes_base64_encoded":"AQI=","mime_type":"video/webm",
                  "uri":" ","url":"https://example.test/video.webm"}}
                """);

        var video = client.decodeInlineVideo(operation);

        assertArrayEquals(new byte[]{1, 2}, video.content());
        assertEquals("generated-video.webm", video.filename());
        assertEquals("video/webm", video.mediaType());
        assertEquals("https://example.test/video.webm", client.extractVideoUri(operation));
        assertEquals("video/mp4", client.decodeInlineVideo(parseOperation("{\"video\":{\"data\":\"AQI=\"}}")).mediaType());
    }

    @Test
    void handlesMissingVideosAndRejectsInvalidInlineBytes() throws Exception {
        var operation = parseOperation("{}");
        assertNull(client.decodeInlineVideo(operation));
        assertEquals("", client.extractVideoUri(operation));
        var invalid = parseOperation("{\"video\":{\"data\":\"!invalid!\"}}");
        assertThrows(UpstreamServiceException.class, () -> client.decodeInlineVideo(invalid));
    }

    @Test
    void rejectsMalformedAndNullOperationResponses() {
        for (String body : List.of("", "null", "{invalid", "{\"response\":[]}")) {
            var exception = assertThrows(UpstreamServiceException.class,
                    () -> client.parseResponse(body, "Invalid operation"));
            assertEquals("Veo", exception.serviceName());
            assertEquals("Invalid operation", exception.getMessage());
        }
    }

    private VeoOperationResponse parseOperation(String response) {
        return client.parseResponse("{\"done\":true,\"extra\":true,\"response\":" + response + "}", "Invalid operation");
    }
}
