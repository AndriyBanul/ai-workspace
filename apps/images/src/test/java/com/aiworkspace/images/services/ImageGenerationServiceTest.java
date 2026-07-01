package com.aiworkspace.images.services;

import com.aiworkspace.images.client.FluxImageClient;
import com.aiworkspace.images.models.GeneratedImage;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageGenerationServiceTest {

    private final ImageGenerationService service = new ImageGenerationService(
            new FluxImageClient("http://localhost", "test-token", "test-model", RestClient.builder().build()) {
                @Override
                public GeneratedImage generate(String description) {
                    return new GeneratedImage("generated-image.png", "image/png", new byte[] {1, 2, 3});
                }
            }
    );

    @Test
    void generatesImageFromDescription() throws IOException, InterruptedException {
        GeneratedImage image = service.generate("A small cabin in a snowy forest.");

        assertEquals("generated-image.png", image.filename());
        assertEquals("image/png", image.mediaType());
        assertArrayEquals(new byte[] {1, 2, 3}, image.content());
    }

    @Test
    void rejectsBlankDescription() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(" ")
        );

        assertEquals("Description must not be blank", exception.getMessage());
    }

    @Test
    void rejectsTooLongDescription() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate("a".repeat(4_001))
        );

        assertEquals("Description must not be longer than 4000 characters", exception.getMessage());
    }
}
