package com.aiworkspace.videos.services;

import com.aiworkspace.videos.models.GeneratedVideo;
import com.aiworkspace.videos.models.VideoAnalysis;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.models.VideoGenerationRequest;
import com.aiworkspace.videos.interfaces.VideoGenerationProvider;
import com.aiworkspace.videos.interfaces.VideoUnderstandingProvider;
import com.aiworkspace.videos.interfaces.YouTubeVideoUnderstandingProvider;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

    private final VideoUnderstandingProvider videoUnderstandingProvider;
    private final YouTubeVideoUnderstandingProvider youTubeVideoUnderstandingProvider;
    private final VideoGenerationProvider videoGenerationProvider;
    private final String descriptionPrompt;
    private final VideoValidator videoValidator;

    public VideoService(
            VideoUnderstandingProvider videoUnderstandingProvider,
            VideoGenerationProvider videoGenerationProvider,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt
    ) {
        this(
                videoUnderstandingProvider,
                videoUnderstandingProvider instanceof YouTubeVideoUnderstandingProvider provider ? provider : null,
                videoGenerationProvider,
                descriptionPrompt,
                new VideoValidator()
        );
    }

    @Autowired
    public VideoService(
            VideoUnderstandingProvider videoUnderstandingProvider,
            YouTubeVideoUnderstandingProvider youTubeVideoUnderstandingProvider,
            VideoGenerationProvider videoGenerationProvider,
            @Value("${ai-workspace.gemini.video-description-prompt:Describe what is happening in this video clearly and concisely.}") String descriptionPrompt,
            VideoValidator videoValidator
    ) {
        this.videoUnderstandingProvider = videoUnderstandingProvider;
        this.youTubeVideoUnderstandingProvider = youTubeVideoUnderstandingProvider;
        this.videoGenerationProvider = videoGenerationProvider;
        this.descriptionPrompt = descriptionPrompt;
        this.videoValidator = videoValidator;
    }

    public VideoDescription describe(String filename, String contentType, byte[] videoContent)
            throws IOException, InterruptedException {
        videoValidator.validateVideo(filename, videoContent);

        String mimeType = videoValidator.mimeType(filename, contentType);
        VideoAnalysis analysis = videoUnderstandingProvider.analyze(videoContent, mimeType, descriptionPrompt);

        return new VideoDescription(
                filename,
                mimeType,
                analysis.summary(),
                analysis.transcript(),
                analysis.language(),
                analysis.segments()
        );
    }

    public GeneratedVideo generate(String description) throws IOException, InterruptedException {
        videoValidator.validateGenerationDescription(description);

        return videoGenerationProvider.generate(description.trim());
    }

    public GeneratedVideo generate(VideoGenerationRequest request) throws IOException, InterruptedException {
        videoValidator.validateGenerationRequest(request);

        return generate(request.description());
    }

    public VideoDescription describeYouTube(String rawUrl) throws IOException, InterruptedException {
        videoValidator.validateYouTubeUrl(rawUrl);
        if (youTubeVideoUnderstandingProvider == null) {
            throw new IllegalStateException("YouTube video understanding provider is not configured");
        }
        String videoId = videoValidator.youtubeVideoId(rawUrl);
        VideoAnalysis analysis = youTubeVideoUnderstandingProvider.analyzeYouTube(
                videoValidator.canonicalYouTubeUrl(rawUrl), descriptionPrompt);
        return new VideoDescription(
                "YouTube video " + videoId,
                "video/youtube",
                analysis.summary(),
                analysis.transcript(),
                analysis.language(),
                analysis.segments());
    }

    public String canonicalYouTubeUrl(String rawUrl) {
        return videoValidator.canonicalYouTubeUrl(rawUrl);
    }

    public String youtubeVideoId(String rawUrl) {
        return videoValidator.youtubeVideoId(rawUrl);
    }

    public String knowledgeText(VideoDescription description) {
        StringBuilder value = new StringBuilder("Visual summary:\n")
                .append(description.description().trim());
        if (description.language() != null && !description.language().isBlank()) {
            value.append("\n\nSpoken language: ").append(description.language().trim());
        }
        if (!description.transcript().isBlank() || !description.segments().isEmpty()) {
            value.append("\n\nSpoken transcript:\n");
            if (description.segments().isEmpty()) {
                value.append(description.transcript().trim());
            } else {
                description.segments().forEach(segment -> value
                        .append('[').append(timestamp(segment.startMilliseconds()))
                        .append(" - ").append(timestamp(segment.endMilliseconds())).append("] ")
                        .append(segment.speaker() == null || segment.speaker().isBlank()
                                ? ""
                                : segment.speaker().trim() + ": ")
                        .append(segment.text().trim()).append('\n'));
            }
        }
        return value.toString().trim();
    }

    private String timestamp(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        long remainder = milliseconds % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, remainder);
    }
}
