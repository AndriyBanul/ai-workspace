package com.aiworkspace.videos.providers;

import java.io.IOException;

public interface VideoUnderstandingProvider {

    String describe(byte[] videoContent, String mimeType, String prompt) throws IOException, InterruptedException;
}
