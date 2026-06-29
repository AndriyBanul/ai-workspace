package com.aiworkspace.audio.services;

import com.aiworkspace.audio.client.PiperClient;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class TextToSpeechService {

    private final PiperClient piperClient;

    public TextToSpeechService(PiperClient piperClient) {
        this.piperClient = piperClient;
    }

    public SynthesizedSpeech synthesize(String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank");
        }

        return piperClient.synthesize(text);
    }
}
