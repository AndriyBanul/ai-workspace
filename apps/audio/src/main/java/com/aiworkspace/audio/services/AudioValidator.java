package com.aiworkspace.audio.services;

import com.aiworkspace.audio.models.TextToSpeechRequest;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AudioValidator {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_TRANSCRIPTION_EXTENSIONS = Set.of("wav", "mp3", "mp4", "avi");

    public void validateTranscriptionFile(String filename, byte[] fileContent) {
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

    public void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text must not be blank");
        }
    }

    public void validateTextToSpeechRequest(TextToSpeechRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
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
