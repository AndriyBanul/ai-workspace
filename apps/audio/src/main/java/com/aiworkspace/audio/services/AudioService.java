package com.aiworkspace.audio.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TextToSpeechRequest;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.interfaces.SpeechToTextProvider;
import com.aiworkspace.audio.interfaces.TextToSpeechProvider;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
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
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final AudioValidator audioValidator;

    public AudioService(SpeechToTextProvider speechToTextProvider, TextToSpeechProvider textToSpeechProvider) {
        this(speechToTextProvider, textToSpeechProvider, null, null, null, new AudioValidator());
    }

    @Autowired
    public AudioService(
            SpeechToTextProvider speechToTextProvider,
            TextToSpeechProvider textToSpeechProvider,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            AudioValidator audioValidator
    ) {
        this.speechToTextProvider = speechToTextProvider;
        this.textToSpeechProvider = textToSpeechProvider;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
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

    public AudioTranscriptionResponse transcribeWorkspaceAudio(
            String ownerId,
            String workspaceId,
            String filename,
            String contentType,
            byte[] content
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(ownerId, workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.AUDIO)
                .originalFilename(filename)
                .contentType(contentType)
                .content(new ByteArrayInputStream(content))
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        AudioTranscription transcription;
        try {
            transcription = transcribe(filename, content);
            knowledgeService.recordAudioInfo(
                    workspace.id(),
                    transcription.filename(),
                    null,
                    chunksForKnowledge(transcription),
                    knowledgeSourceMetadata(workspaceFile.id())
            );
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        return new AudioTranscriptionResponse(
                transcription.filename(),
                content.length,
                transcription.language(),
                transcription.text(),
                transcription.segments()
        );
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
