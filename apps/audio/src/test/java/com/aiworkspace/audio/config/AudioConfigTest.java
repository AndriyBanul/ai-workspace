package com.aiworkspace.audio.config;

import com.aiworkspace.audio.client.PiperClient;
import com.aiworkspace.audio.services.AudioValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioConfigTest {

    @Test
    void bindsLimitsWiresPiperAndClosesDeadlineScheduler() {
        ScheduledExecutorService scheduler;
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "ai-workspace.audio.synthesis.max-text-characters=10",
                    "ai-workspace.audio.synthesis.max-audio-size=2MB",
                    "ai-workspace.audio.synthesis.request-timeout=4s");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(AudioConfig.class, AudioValidator.class, PiperClient.class);
            context.refresh();

            var properties = context.getBean(AudioSynthesisProperties.class);
            assertEquals(10, properties.maxTextCharacters());
            assertEquals(DataSize.ofMegabytes(2), properties.maxAudioSize());
            assertEquals(Duration.ofSeconds(4), properties.requestTimeout());
            assertEquals(1, properties.maxConcurrentRequests());
            assertNotNull(context.getBean(PiperClient.class));
            scheduler = context.getBean("piperDeadlineScheduler", ScheduledExecutorService.class);
        }
        assertTrue(scheduler.isShutdown());
    }

    @Test
    void rejectsUnsafeLimitConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxTextCharacters(0).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxEvents(0).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxConcurrentRequests(0).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxAudioSize(DataSize.ofBytes(-1)).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxAudioSize(DataSize.ofGigabytes(2)).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().maxHeaderSize(DataSize.ofBytes(0)).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().requestTimeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().readTimeout(Duration.ofNanos(1)).build());
        assertThrows(IllegalArgumentException.class, () -> AudioSynthesisProperties.builder().connectTimeout(Duration.ofDays(30)).build());
    }
}
