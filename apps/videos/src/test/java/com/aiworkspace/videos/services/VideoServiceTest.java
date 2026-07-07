package com.aiworkspace.videos.services;

import com.aiworkspace.videos.client.GeminiVideoClient;
import com.aiworkspace.videos.client.VeoVideoClient;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VideoService service = new VideoService(
            new GeminiVideoClient("http://localhost", "test-key", "test-model", RestClient.builder().build(), OBJECT_MAPPER) {
                @Override
                public String describe(byte[] videoContent, String mimeType, String prompt) {
                    return "A concise video description.";
                }
            },
            new VeoVideoClient(
                    "http://localhost",
                    "test-key",
                    "test-model",
                    "16:9",
                    RestClient.builder().build(),
                    OBJECT_MAPPER
            ) {
                @Override
                public GeneratedVideo generate(String description) {
                    return new GeneratedVideo("generated-video.mp4", "video/mp4", new byte[] {1, 2, 3});
                }
            },
            "Describe this video."
    );

    @Test
    void describesSupportedVideo() throws IOException, InterruptedException {
        var description = service.describe("clip.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertEquals("clip.mp4", description.filename());
        assertEquals("video/mp4", description.mimeType());
        assertEquals("A concise video description.", description.description());
    }

    @Test
    void generatesVideoFromDescription() throws IOException, InterruptedException {
        GeneratedVideo video = service.generate("A cinematic shot of a mountain lake.");

        assertEquals("generated-video.mp4", video.filename());
        assertEquals("video/mp4", video.mediaType());
        assertArrayEquals(new byte[] {1, 2, 3}, video.content());
    }

    @Test
    void rejectsUnsupportedVideoType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.describe("clip.mkv", "video/x-matroska", new byte[] {1, 2, 3})
        );

        assertEquals("Only MP4, MOV, WEBM, MPEG, MPG, and AVI videos are supported", exception.getMessage());
    }

    @Test
    void rejectsEmptyVideo() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.describe("clip.mp4", "video/mp4", new byte[0])
        );

        assertEquals("File must not be empty", exception.getMessage());
    }

    @Test
    void rejectsBlankGenerationDescription() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(" ")
        );

        assertEquals("Description must not be blank", exception.getMessage());
    }
}
