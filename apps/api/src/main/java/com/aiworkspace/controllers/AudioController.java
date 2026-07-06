package com.aiworkspace.controllers;

import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.models.TextToSpeechRequest;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.users.services.UserAccountService;
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
    private final UserAccountService userAccountService;

    public AudioController(AudioService audioService, UserAccountService userAccountService) {
        this.audioService = audioService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioTranscriptionResponse> transcribe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException, InterruptedException {
        return ResponseEntity.ok(audioService.transcribeWorkspaceAudio(
                userAccountService.currentUserId(authentication),
                workspaceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        ));
    }

    @PostMapping(path = "/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TextToSpeechRequest request) throws IOException {
        SynthesizedSpeech speech = audioService.synthesize(request);

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
}
