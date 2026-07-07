package com.aiworkspace.audio.providers;

import com.aiworkspace.audio.models.WhisperTranscriptionResponse;
import java.io.IOException;

public interface SpeechToTextProvider {

    WhisperTranscriptionResponse transcribe(String filename, byte[] fileContent)
            throws IOException, InterruptedException;
}
