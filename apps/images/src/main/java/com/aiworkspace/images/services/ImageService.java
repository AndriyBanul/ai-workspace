package com.aiworkspace.images.services;

import com.aiworkspace.images.client.FluxImageClient;
import com.aiworkspace.images.client.GeminiImageClient;
import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_GENERATION_DESCRIPTION_LENGTH = 4_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final GeminiImageClient geminiImageClient;
    private final FluxImageClient fluxImageClient;
    private final String descriptionPrompt;

    public ImageService(
            GeminiImageClient geminiImageClient,
            FluxImageClient fluxImageClient,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt
    ) {
        this.geminiImageClient = geminiImageClient;
        this.fluxImageClient = fluxImageClient;
        this.descriptionPrompt = descriptionPrompt;
    }

    public ImageDescription describe(String filename, String contentType, byte[] imageContent)
            throws IOException, InterruptedException {
        validateImage(filename, imageContent);

        String mimeType = mimeType(filename, contentType);
        String description = geminiImageClient.describe(imageContent, mimeType, descriptionPrompt);

        return new ImageDescription(filename, mimeType, description);
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        validateGenerationDescription(description);

        return fluxImageClient.generate(description.trim());
    }

    private void validateImage(String filename, byte[] imageContent) {
        if (imageContent == null || imageContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (imageContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 10MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only JPG, PNG, and WEBP images are supported");
        }
    }

    private void validateGenerationDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }

        if (description.length() > MAX_GENERATION_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must not be longer than 4000 characters");
        }
    }

    private String mimeType(String filename, String contentType) {
        String mimeType = MIME_TYPES_BY_EXTENSION.get(extension(filename));
        if (contentType != null && contentType.equals(mimeType)) {
            return contentType;
        }

        return mimeType;
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
