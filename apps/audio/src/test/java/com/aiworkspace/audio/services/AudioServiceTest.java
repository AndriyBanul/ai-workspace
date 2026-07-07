package com.aiworkspace.audio.services;

import com.aiworkspace.audio.client.PiperClient;
import com.aiworkspace.audio.client.WhisperClient;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AudioService service = new AudioService(
            new WhisperClient("http://localhost", RestClient.builder().build(), OBJECT_MAPPER) {
                @Override
                public TranscriptionResponse transcribe(String filename, byte[] fileContent) {
                    return new TranscriptionResponse("Transcript text", "en");
                }
            },
            new PiperClient("localhost", 10200, OBJECT_MAPPER) {
                @Override
                public SynthesizedSpeech synthesize(String text) {
                    return new SynthesizedSpeech("speech.wav", new byte[] {1, 2, 3});
                }
            }
    );

    @Test
    void transcribesSupportedAudio() throws IOException, InterruptedException {
        var transcription = service.transcribe("meeting.mp3", new byte[] {1, 2, 3});

        assertEquals("meeting.mp3", transcription.filename());
        assertEquals("Transcript text", transcription.text());
        assertEquals("en", transcription.language());
    }

    @Test
    void synthesizesSpeech() throws IOException {
        SynthesizedSpeech speech = service.synthesize("Hello");

        assertEquals("speech.wav", speech.filename());
        assertArrayEquals(new byte[] {1, 2, 3}, speech.wavContent());
    }

    @Test
    void rejectsUnsupportedAudioType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.transcribe("meeting.flac", new byte[] {1})
        );

        assertEquals("Only WAV, MP3, MP4, and AVI files are supported", exception.getMessage());
    }

    @Test
    void rejectsEmptyAudio() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.transcribe("meeting.mp3", new byte[0])
        );

        assertEquals("File must not be empty", exception.getMessage());
    }

    @Test
    void rejectsBlankSpeechText() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.synthesize(" ")
        );

        assertEquals("Text must not be blank", exception.getMessage());
    }
}
