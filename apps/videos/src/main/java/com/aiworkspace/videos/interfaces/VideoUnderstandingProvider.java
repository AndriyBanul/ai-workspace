package com.aiworkspace.videos.interfaces;

import java.io.IOException;

public interface VideoUnderstandingProvider {

    String describe(byte[] videoContent, String mimeType, String prompt) throws IOException, InterruptedException;
}
