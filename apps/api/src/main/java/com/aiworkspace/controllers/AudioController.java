package com.aiworkspace.controllers;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.files.models.CreateWorkspaceFileRequest;
import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.models.WorkspaceFileSourceType;
import com.aiworkspace.files.services.WorkspaceFileService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.AudioTranscriptionResponse;
import com.aiworkspace.users.services.UserAccountService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {

    private final AudioService audioService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final UserAccountService userAccountService;

    public AudioController(
            AudioService audioService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            UserAccountService userAccountService
    ) {
        this.audioService = audioService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioTranscriptionResponse> transcribe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(userAccountService.currentUserId(authentication), workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.AUDIO)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .content(file.getInputStream())
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        AudioTranscription transcription;
        try {
            transcription = audioService.transcribe(
                    file.getOriginalFilename(),
                    file.getBytes()
            );
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

        return ResponseEntity.ok(new AudioTranscriptionResponse(
                transcription.filename(),
                file.getSize(),
                transcription.language(),
                transcription.text()
        ));
    }

    @PostMapping(path = "/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TextToSpeechRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        SynthesizedSpeech speech = audioService.synthesize(request.text());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(speech.filename())
                                .build()
                                .toString()
                )
                .body(speech.wavContent());
    }

    public record TextToSpeechRequest(String text) {
    }
}
