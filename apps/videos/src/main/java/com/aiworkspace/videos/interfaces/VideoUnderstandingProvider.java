package com.aiworkspace.videos.interfaces;

import com.aiworkspace.videos.models.VideoAnalysis;
import java.io.IOException;

public interface VideoUnderstandingProvider {

    VideoAnalysis analyze(byte[] videoContent, String mimeType, String prompt) throws IOException, InterruptedException;
}
