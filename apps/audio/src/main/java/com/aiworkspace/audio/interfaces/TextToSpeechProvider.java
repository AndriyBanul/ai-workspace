package com.aiworkspace.audio.interfaces;

import com.aiworkspace.audio.models.SynthesizedSpeech;
import java.io.IOException;

public interface TextToSpeechProvider {

    SynthesizedSpeech synthesize(String text) throws IOException;
}
