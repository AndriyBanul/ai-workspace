package com.aiworkspace.audio.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TextToSpeechRequest;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.interfaces.SpeechToTextProvider;
import com.aiworkspace.audio.interfaces.TextToSpeechProvider;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.shared.media.TranscriptSegment;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AudioService {

    private static final String TRANSCRIPT_SECTION_ID = "transcript";
    private static final String TRANSCRIPT_PARSER_VERSION = "ai-workspace-audio-transcript-v1";

    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final AudioValidator audioValidator;

    public AudioService(SpeechToTextProvider speechToTextProvider, TextToSpeechProvider textToSpeechProvider) {
        this(speechToTextProvider, textToSpeechProvider, new AudioValidator());
    }

    @Autowired
    public AudioService(
            SpeechToTextProvider speechToTextProvider,
            TextToSpeechProvider textToSpeechProvider,
            AudioValidator audioValidator
    ) {
        this.speechToTextProvider = speechToTextProvider;
        this.textToSpeechProvider = textToSpeechProvider;
        this.audioValidator = audioValidator;
    }

    public AudioTranscription transcribe(String filename, byte[] fileContent) throws IOException, InterruptedException {
        audioValidator.validateTranscriptionFile(filename, fileContent);

        TranscriptionResponse transcription = speechToTextProvider.transcribe(filename, fileContent);

        return new AudioTranscription(
                filename,
                transcription.text(),
                transcription.language(),
                transcription.segments()
        );
    }

    public SynthesizedSpeech synthesize(String text) throws IOException {
        audioValidator.validateText(text);
        return textToSpeechProvider.synthesize(text);
    }

    public SynthesizedSpeech synthesize(TextToSpeechRequest request) throws IOException {
        audioValidator.validateTextToSpeechRequest(request);

        return synthesize(request.text());
    }

    public String knowledgeText(AudioTranscription transcription) {
        StringBuilder value = new StringBuilder();
        if (transcription.language() != null && !transcription.language().isBlank()) {
            value.append("Language: ").append(transcription.language().trim()).append("\n\n");
        }
        value.append("Transcript:\n");
        if (transcription.segments().isEmpty()) {
            return value.append(transcription.text().trim()).toString();
        }
        transcription.segments().forEach(segment -> value
                .append('[').append(timestamp(segment.startMilliseconds()))
                .append(" - ").append(timestamp(segment.endMilliseconds())).append("] ")
                .append(segment.speaker() == null || segment.speaker().isBlank()
                        ? ""
                        : segment.speaker().trim() + ": ")
                .append(segment.text().trim()).append('\n'));
        return value.toString().trim();
    }

    public List<KnowledgeChunk> chunksForKnowledge(AudioTranscription transcription) {
        if (transcription == null) {
            throw new IllegalArgumentException("Audio transcription must not be null");
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (TranscriptSegment segment : transcription.segments()) {
            if (segment == null || segment.text() == null || segment.text().isBlank()) {
                continue;
            }
            chunks.add(KnowledgeChunk.builder()
                    .sequence(chunks.size() + 1)
                    .content(segment.text().trim())
                    .heading("Audio transcript")
                    .sectionId(TRANSCRIPT_SECTION_ID)
                    .startMilliseconds(segment.startMilliseconds())
                    .endMilliseconds(segment.endMilliseconds())
                    .speaker(normalizedSpeaker(segment.speaker()))
                    .build());
        }

        if (!chunks.isEmpty()) {
            return List.copyOf(chunks);
        }
        if (transcription.text() == null || transcription.text().isBlank()) {
            throw new IllegalArgumentException("Audio transcript must not be blank");
        }
        return List.of(KnowledgeChunk.builder()
                .sequence(1)
                .content(transcription.text().trim())
                .heading("Audio transcript")
                .sectionId(TRANSCRIPT_SECTION_ID)
                .build());
    }

    public KnowledgeSourceMetadata knowledgeSourceMetadata(String sourceId) {
        return new KnowledgeSourceMetadata(sourceId, null, Instant.now(), TRANSCRIPT_PARSER_VERSION);
    }

    private String normalizedSpeaker(String speaker) {
        return speaker == null || speaker.isBlank() ? null : speaker.trim();
    }

    private String timestamp(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        long remainder = milliseconds % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, remainder);
    }
}
