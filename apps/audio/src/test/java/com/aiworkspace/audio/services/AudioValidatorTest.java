package com.aiworkspace.audio.services;

import com.aiworkspace.audio.config.AudioSynthesisProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioValidatorTest {

    private final AudioValidator validator = new AudioValidator(
            AudioSynthesisProperties.builder().maxTextCharacters(3).build());

    @Test
    void countsUnicodeCodePointsAndAcceptsTheExactLimit() {
        assertDoesNotThrow(() -> validator.validateText("abc"));
        assertDoesNotThrow(() -> validator.validateText("😀😀😀"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateText("abcd"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateText("😀😀😀😀"));
    }
}
