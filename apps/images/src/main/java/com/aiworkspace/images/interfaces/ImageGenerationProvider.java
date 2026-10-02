package com.aiworkspace.images.interfaces;

import com.aiworkspace.images.models.GeneratedImage;
import java.io.IOException;

public interface ImageGenerationProvider {

    GeneratedImage generate(String description) throws IOException, InterruptedException;
}
