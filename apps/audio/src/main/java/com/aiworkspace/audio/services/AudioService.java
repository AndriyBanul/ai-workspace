package com.aiworkspace.audio.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.TextToSpeechRequest;
import com.aiworkspace.audio.models.TranscriptionResponse;
import com.aiworkspace.audio.providers.SpeechToTextProvider;
import com.aiworkspace.audio.providers.TextToSpeechProvider;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AudioService {

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
                transcription.language()
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
        audioValidator.validateTextToSpeechRequest(request);

        return synthesize(request.text());
    }
}
