package com.aiworkspace.audio.services;

import com.aiworkspace.audio.client.PiperClient;
import com.aiworkspace.audio.client.WhisperClient;
import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
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
                    return new TranscriptionResponse(
                            "Transcript text",
                            "en",
                            List.of(new TranscriptSegment(1_250, 3_500, "Speaker 1", "Transcript text"))
                    );
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
        assertEquals(1, transcription.segments().size());
        assertEquals(1_250, transcription.segments().getFirst().startMilliseconds());
    }

    @Test
    void formatsTimedTranscriptForWorkspaceKnowledge() throws IOException, InterruptedException {
        var transcription = service.transcribe("meeting.mp3", new byte[] {1, 2, 3});

        assertEquals(
                "Language: en\n\nTranscript:\n[00:00:01.250 - 00:00:03.500] Speaker 1: Transcript text",
                service.knowledgeText(transcription)
        );
    }

    @Test
    void createsIndependentlySearchableTimedTranscriptChunks() {
        AudioTranscription transcription = new AudioTranscription(
                "meeting.mp3",
                "First point. Second point.",
                "en",
                List.of(
                        new TranscriptSegment(1_250, 3_500, " Speaker 1 ", " First point. "),
                        new TranscriptSegment(3_500, 5_750, null, "Second point.")
                )
        );

        var chunks = service.chunksForKnowledge(transcription);

        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).sequence());
        assertEquals("First point.", chunks.get(0).content());
        assertEquals("transcript", chunks.get(0).sectionId());
        assertEquals(1_250L, chunks.get(0).startMilliseconds());
        assertEquals(3_500L, chunks.get(0).endMilliseconds());
        assertEquals("Speaker 1", chunks.get(0).speaker());
        assertEquals(2, chunks.get(1).sequence());
    }

    @Test
    void createsOneUntimedChunkWhenProviderReturnsNoSegments() {
        AudioTranscription transcription = new AudioTranscription(
                "meeting.mp3",
                " Complete transcript. ",
                "en"
        );

        var chunks = service.chunksForKnowledge(transcription);

        assertEquals(1, chunks.size());
        assertEquals("Complete transcript.", chunks.getFirst().content());
        assertEquals("transcript", chunks.getFirst().sectionId());
        assertEquals(null, chunks.getFirst().startMilliseconds());
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
