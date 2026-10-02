package com.aiworkspace.images.services;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.models.ImageGenerationRequest;
import com.aiworkspace.images.interfaces.ImageGenerationProvider;
import com.aiworkspace.images.interfaces.ImageUnderstandingProvider;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

    private final ImageUnderstandingProvider imageUnderstandingProvider;
    private final ImageGenerationProvider imageGenerationProvider;
    private final String descriptionPrompt;
    private final ImageValidator imageValidator;

    public ImageService(
            ImageUnderstandingProvider imageUnderstandingProvider,
            ImageGenerationProvider imageGenerationProvider,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt
    ) {
        this(imageUnderstandingProvider, imageGenerationProvider, descriptionPrompt, new ImageValidator());
    }

    @Autowired
    public ImageService(
            ImageUnderstandingProvider imageUnderstandingProvider,
            ImageGenerationProvider imageGenerationProvider,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt,
            ImageValidator imageValidator
    ) {
        this.imageUnderstandingProvider = imageUnderstandingProvider;
        this.imageGenerationProvider = imageGenerationProvider;
        this.descriptionPrompt = descriptionPrompt;
        this.imageValidator = imageValidator;
    }

    public ImageDescription describe(String filename, String contentType, byte[] imageContent)
            throws IOException, InterruptedException {
        imageValidator.validateImage(filename, imageContent);

        String mimeType = imageValidator.mimeType(filename, contentType);
        String description = imageUnderstandingProvider.describe(imageContent, mimeType, descriptionPrompt);

        return new ImageDescription(filename, mimeType, description);
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        imageValidator.validateGenerationDescription(description);

        return imageGenerationProvider.generate(description.trim());
    }

    public GeneratedImage generate(ImageGenerationRequest request) throws IOException, InterruptedException {
        imageValidator.validateGenerationRequest(request);

        return generate(request.description());
    }
}
