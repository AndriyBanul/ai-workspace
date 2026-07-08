package com.aiworkspace.images.interfaces;

import java.io.IOException;

public interface ImageUnderstandingProvider {

    String describe(byte[] imageContent, String mimeType, String prompt) throws IOException, InterruptedException;
}
