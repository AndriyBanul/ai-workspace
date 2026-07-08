package com.aiworkspace.audio.interfaces;

import com.aiworkspace.audio.models.TranscriptionResponse;
import java.io.IOException;

public interface SpeechToTextProvider {

    TranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException;
}
