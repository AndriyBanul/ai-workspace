package com.aiworkspace.images.services;

import com.aiworkspace.images.client.FluxImageClient;
import com.aiworkspace.images.client.GeminiImageClient;
import com.aiworkspace.images.models.GeneratedImage;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageServiceTest {

    private final ImageService service = new ImageService(
            new GeminiImageClient("http://localhost", "test-key", "test-model", RestClient.builder().build()) {
                @Override
                public String describe(byte[] imageContent, String mimeType, String prompt) {
                    return "A concise image description.";
                }
            },
            new FluxImageClient("http://localhost", "test-token", "test-model", RestClient.builder().build()) {
                @Override
                public GeneratedImage generate(String description) {
                    return new GeneratedImage("generated-image.png", "image/png", new byte[] {1, 2, 3});
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
    void generatesImageFromDescription() throws IOException, InterruptedException {
        GeneratedImage image = service.generate("A small cabin in a snowy forest.");

        assertEquals("generated-image.png", image.filename());
        assertEquals("image/png", image.mediaType());
        assertArrayEquals(new byte[] {1, 2, 3}, image.content());
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

    @Test
    void rejectsBlankGenerationDescription() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(" ")
        );

        assertEquals("Description must not be blank", exception.getMessage());
    }
}
