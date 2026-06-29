package com.aiworkspace.controllers;

import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.services.TextToSpeechService;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/audio")
public class TextToSpeechController {

    private final TextToSpeechService textToSpeechService;

    public TextToSpeechController(TextToSpeechService textToSpeechService) {
        this.textToSpeechService = textToSpeechService;
    }

    @PostMapping(path = "/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TextToSpeechRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body must not be empty");
        }

        try {
            SynthesizedSpeech speech = textToSpeechService.synthesize(request.text());

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
