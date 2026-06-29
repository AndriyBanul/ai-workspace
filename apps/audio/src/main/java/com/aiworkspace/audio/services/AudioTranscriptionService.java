package com.aiworkspace.audio.services;

import com.aiworkspace.audio.client.WhisperClient;
import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.models.WhisperTranscriptionResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AudioTranscriptionService {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("wav", "mp3", "mp4", "avi");

    private final WhisperClient whisperClient;

    public AudioTranscriptionService(WhisperClient whisperClient) {
        this.whisperClient = whisperClient;
    }

    public AudioTranscription transcribe(String filename, byte[] fileContent) throws IOException, InterruptedException {
        validate(filename, fileContent);

        WhisperTranscriptionResponse transcription = whisperClient.transcribe(filename, fileContent);

        return new AudioTranscription(
                filename,
                transcription.text(),
                transcription.language()
        );
    }

    private void validate(String filename, byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (fileContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 25MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
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
