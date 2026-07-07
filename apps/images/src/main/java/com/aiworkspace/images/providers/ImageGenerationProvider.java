package com.aiworkspace.images.providers;

import com.aiworkspace.images.models.GeneratedImage;
import java.io.IOException;

public interface ImageGenerationProvider {

    GeneratedImage generate(String description) throws IOException, InterruptedException;
}
