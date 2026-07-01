package com.aiworkspace.videos.services;

import com.aiworkspace.videos.client.GeminiVideoClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoDescriptionServiceTest {

    private final VideoDescriptionService service = new VideoDescriptionService(
            new GeminiVideoClient("http://localhost", "test-key", "test-model") {
                @Override
                public String describe(byte[] videoContent, String mimeType, String prompt) {
                    return "A concise video description.";
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
}
