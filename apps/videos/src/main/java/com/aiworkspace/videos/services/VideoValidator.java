package com.aiworkspace.videos.services;

import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VideoValidator {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_GENERATION_DESCRIPTION_LENGTH = 4_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "mov", "webm", "mpeg", "mpg", "avi");
    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com"
    );
    private static final String YOUTUBE_SHORT_HOST = "youtu.be";
    private static final String VIDEO_ID_PATTERN = "[A-Za-z0-9_-]{11}";
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

        String extension = extractExtension(filename);
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

    public void validateYouTubeIngestionRequest(YouTubeVideoIngestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
        if (request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
        validateYouTubeUrl(request.url());
    }

    public void validateYouTubeUrl(String rawUrl) {
        extractYouTubeVideoId(rawUrl);
    }

    public String canonicalizeYouTubeUrl(String rawUrl) {
        return "https://www.youtube.com/watch?v=" + extractYouTubeVideoId(rawUrl);
    }

    public String extractYouTubeVideoId(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("YouTube URL must not be blank");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw createInvalidYouTubeUrlException();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw createInvalidYouTubeUrlException();
        }

        if (!YOUTUBE_SHORT_HOST.equals(host) && !YOUTUBE_HOSTS.contains(host)) {
            throw createInvalidYouTubeUrlException();
        }
        String videoId;
        try {
            videoId = YOUTUBE_SHORT_HOST.equals(host)
                    ? extractFirstPathSegment(uri.getPath())
                    : extractVideoIdFromYouTubePath(uri);
        } catch (IllegalArgumentException exception) {
            throw createInvalidYouTubeUrlException();
        }
        if (videoId == null || !videoId.matches(VIDEO_ID_PATTERN)) {
            throw createInvalidYouTubeUrlException();
        }
        return videoId;
    }

    private String extractVideoIdFromYouTubePath(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("/watch".equals(path)) {
            return readQueryParameter(uri.getRawQuery(), "v");
        }
        for (String prefix : Set.of("/shorts/", "/embed/", "/live/")) {
            if (path.startsWith(prefix)) {
                return extractFirstPathSegment(path.substring(prefix.length() - 1));
            }
        }
        return null;
    }

    private String readQueryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String parameter : rawQuery.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String extractFirstPathSegment(String path) {
        if (path == null) {
            return null;
        }
        return path.replaceFirst("^/+", "").split("/", 2)[0];
    }

    private IllegalArgumentException createInvalidYouTubeUrlException() {
        return new IllegalArgumentException("URL must identify one public YouTube video");
    }

    public String resolveMimeType(String filename, String contentType) {
        String mimeType = MIME_TYPES_BY_EXTENSION.get(extractExtension(filename));
        if (contentType != null && contentType.equals(mimeType)) {
            return contentType;
        }

        return mimeType;
    }

    private String extractExtension(String filename) {
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
