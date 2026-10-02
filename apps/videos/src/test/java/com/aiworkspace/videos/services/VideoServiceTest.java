package com.aiworkspace.videos.services;

import com.aiworkspace.videos.client.GeminiVideoClient;
import com.aiworkspace.videos.client.VeoVideoClient;
import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoAnalysis;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
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
                public VideoAnalysis analyze(byte[] videoContent, String mimeType, String prompt) {
                    return new VideoAnalysis(
                            "A concise video description.",
                            "Hello from the video.",
                            "en",
                            List.of(new TranscriptSegment(500, 2_000, "Presenter", "Hello from the video."))
                    );
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
        assertEquals("Hello from the video.", description.transcript());
        assertEquals("en", description.language());
        assertEquals(1, description.segments().size());
    }

    @Test
    void formatsVisualSummaryAndTimedTranscriptForWorkspaceKnowledge() throws IOException, InterruptedException {
        var description = service.describe("clip.mp4", "video/mp4", new byte[] {1, 2, 3});

        assertEquals(
                "Visual summary:\nA concise video description.\n\n"
                        + "Spoken language: en\n\nSpoken transcript:\n"
                        + "[00:00:00.500 - 00:00:02.000] Presenter: Hello from the video.",
                service.knowledgeText(description)
        );
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

    @Test
    void canonicalizesSupportedYouTubeUrls() {
        VideoValidator validator = new VideoValidator();

        assertEquals(
                "https://www.youtube.com/watch?v=9hE5-98ZeCg",
                validator.canonicalYouTubeUrl("https://youtu.be/9hE5-98ZeCg?t=4")
        );
        assertEquals(
                "https://www.youtube.com/watch?v=9hE5-98ZeCg",
                validator.canonicalYouTubeUrl("https://www.youtube.com/shorts/9hE5-98ZeCg")
        );
    }

    @Test
    void rejectsNonYouTubeAndPlaylistUrls() {
        VideoValidator validator = new VideoValidator();

        assertEquals(
                "URL must identify one public YouTube video",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.canonicalYouTubeUrl("https://example.com/watch?v=9hE5-98ZeCg")
                ).getMessage()
        );
        assertEquals(
                "URL must identify one public YouTube video",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.canonicalYouTubeUrl("https://www.youtube.com/playlist?list=PL123")
                ).getMessage()
        );
    }
}
