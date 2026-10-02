package com.aiworkspace.videos.interfaces;

import com.aiworkspace.videos.models.GeneratedVideo;
import java.io.IOException;

public interface VideoGenerationProvider {

    GeneratedVideo generate(String description) throws IOException, InterruptedException;
}
