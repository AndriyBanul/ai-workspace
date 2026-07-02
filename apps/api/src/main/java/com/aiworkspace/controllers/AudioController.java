package com.aiworkspace.controllers;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.models.AudioTranscriptionResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

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
    ) {
        try {
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
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_GATEWAY, "Interrupted while transcribing audio file", exception);
        } catch (IOException exception) {
            log.warn("Failed to transcribe audio file '{}'", file.getOriginalFilename(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to transcribe audio file", exception);
        }
    }

    @PostMapping(path = "/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TextToSpeechRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
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
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to synthesize speech", exception);
        }
    }

    public record TextToSpeechRequest(String text) {
    }
}
