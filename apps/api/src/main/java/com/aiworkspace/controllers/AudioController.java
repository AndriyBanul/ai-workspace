package com.aiworkspace.controllers;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.AudioTranscriptionResponse;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final KnowledgeService knowledgeService;

    public AudioController(AudioService audioService, KnowledgeService knowledgeService) {
        this.audioService = audioService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioTranscriptionResponse> transcribe(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam("file") MultipartFile file
    ) throws IOException, InterruptedException {
        AudioTranscription transcription = audioService.transcribe(
                file.getOriginalFilename(),
                file.getBytes()
        );
        knowledgeService.recordAudioInfo(workspaceId, transcription.filename(), null, transcription.text());

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
