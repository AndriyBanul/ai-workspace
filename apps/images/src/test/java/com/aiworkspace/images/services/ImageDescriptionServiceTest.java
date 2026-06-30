package com.aiworkspace.images.services;

import com.aiworkspace.images.client.GeminiImageClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageDescriptionServiceTest {

    private final ImageDescriptionService service = new ImageDescriptionService(
            new GeminiImageClient("http://localhost", "test-key", "test-model") {
                @Override
                public String describe(byte[] imageContent, String mimeType, String prompt) {
                    return "A concise image description.";
                }
            },
            "Describe this image."
    );

    @Test
    void describesSupportedImage() throws IOException, InterruptedException {
        var description = service.describe("photo.png", "image/png", new byte[] {1, 2, 3});

        assertEquals("photo.png", description.filename());
        assertEquals("image/png", description.mimeType());
        assertEquals("A concise image description.", description.description());
    }

    @Test
    void rejectsUnsupportedImageType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.describe("photo.gif", "image/gif", new byte[] {1, 2, 3})
        );

        assertEquals("Only JPG, PNG, and WEBP images are supported", exception.getMessage());
    }

    @Test
    void rejectsEmptyImage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.describe("photo.png", "image/png", new byte[0])
        );

        assertEquals("File must not be empty", exception.getMessage());
    }
}
