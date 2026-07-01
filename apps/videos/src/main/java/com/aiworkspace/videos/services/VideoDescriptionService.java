package com.aiworkspace.videos.services;

import com.aiworkspace.videos.client.GeminiVideoClient;
import com.aiworkspace.videos.models.VideoDescription;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoDescriptionService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "mov", "webm", "mpeg", "mpg", "avi");
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime",
            "webm", "video/webm",
            "mpeg", "video/mpeg",
            "mpg", "video/mpeg",
            "avi", "video/x-msvideo"
    );

    private final GeminiVideoClient geminiVideoClient;
    private final String prompt;

    public VideoDescriptionService(
            GeminiVideoClient geminiVideoClient,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String prompt
    ) {
        this.geminiVideoClient = geminiVideoClient;
        this.prompt = prompt;
    }

    public VideoDescription describe(String filename, String contentType, byte[] videoContent)
            throws IOException, InterruptedException {
        validate(filename, videoContent);

        String mimeType = mimeType(filename, contentType);
        String description = geminiVideoClient.describe(videoContent, mimeType, prompt);

        return new VideoDescription(filename, mimeType, description);
    }

    private void validate(String filename, byte[] videoContent) {
        if (videoContent == null || videoContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (videoContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 20MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only MP4, MOV, WEBM, MPEG, MPG, and AVI videos are supported");
        }
    }

    private String mimeType(String filename, String contentType) {
        String mimeType = MIME_TYPES_BY_EXTENSION.get(extension(filename));
        if (contentType != null && contentType.equals(mimeType)) {
            return contentType;
        }

        return mimeType;
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
