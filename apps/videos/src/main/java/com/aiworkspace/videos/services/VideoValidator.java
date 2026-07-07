package com.aiworkspace.videos.services;

import com.aiworkspace.videos.models.VideoGenerationRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VideoValidator {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_GENERATION_DESCRIPTION_LENGTH = 4_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "mov", "webm", "mpeg", "mpg", "avi");
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime",
            "webm", "video/webm",
            "mpeg", "video/mpeg",
            "mpg", "video/mpeg",
            "avi", "video/x-msvideo"
    );

    public void validateVideo(String filename, byte[] videoContent) {
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

    public void validateGenerationDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }

        if (description.length() > MAX_GENERATION_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must not be longer than 4000 characters");
        }
    }

    public void validateGenerationRequest(VideoGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }

    public String mimeType(String filename, String contentType) {
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
