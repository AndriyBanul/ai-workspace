package com.aiworkspace.images.services;

import com.aiworkspace.images.client.FluxImageClient;
import com.aiworkspace.images.models.GeneratedImage;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    private final FluxImageClient fluxImageClient;

    public ImageGenerationService(FluxImageClient fluxImageClient) {
        this.fluxImageClient = fluxImageClient;
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        validate(description);

        return fluxImageClient.generate(description.trim());
    }

    private void validate(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must not be longer than 4000 characters");
        }
    }
}
