package com.aiworkspace.videos.interfaces;

import com.aiworkspace.videos.models.VideoAnalysis;
import java.io.IOException;

public interface YouTubeVideoUnderstandingProvider {

    VideoAnalysis analyzeYouTube(String youtubeUrl, String prompt) throws IOException, InterruptedException;
}
