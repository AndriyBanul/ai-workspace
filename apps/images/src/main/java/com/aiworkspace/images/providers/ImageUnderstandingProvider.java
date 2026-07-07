package com.aiworkspace.images.providers;

import java.io.IOException;

public interface ImageUnderstandingProvider {

    String describe(byte[] imageContent, String mimeType, String prompt) throws IOException, InterruptedException;
}
