package com.aiworkspace.audio.services;

import com.aiworkspace.audio.client.PiperClient;
import com.aiworkspace.audio.client.WhisperClient;
import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TextToSpeechRequest;
import com.aiworkspace.audio.models.WhisperTranscriptionResponse;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AudioService {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_TRANSCRIPTION_EXTENSIONS = Set.of("wav", "mp3", "mp4", "avi");

    private final WhisperClient whisperClient;
    private final PiperClient piperClient;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;

    public AudioService(WhisperClient whisperClient, PiperClient piperClient) {
        this(whisperClient, piperClient, null, null, null);
    }

    @Autowired
    public AudioService(
            WhisperClient whisperClient,
            PiperClient piperClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService
    ) {
        this.whisperClient = whisperClient;
        this.piperClient = piperClient;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
    }

    public AudioTranscription transcribe(String filename, byte[] fileContent) throws IOException, InterruptedException {
        validateTranscriptionFile(filename, fileContent);

        WhisperTranscriptionResponse transcription = whisperClient.transcribe(filename, fileContent);

        return new AudioTranscription(
                filename,
                transcription.text(),
                transcription.language()
        );
    }

    public SynthesizedSpeech synthesize(String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank");
        }

        return piperClient.synthesize(text);
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
            knowledgeService.recordAudioInfo(workspace.id(), transcription.filename(), null, transcription.text());
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
                transcription.text()
        );
    }

    public SynthesizedSpeech synthesize(TextToSpeechRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return synthesize(request.text());
    }

    private void validateTranscriptionFile(String filename, byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (fileContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 25MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_TRANSCRIPTION_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only WAV, MP3, MP4, and AVI files are supported");
        }
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
