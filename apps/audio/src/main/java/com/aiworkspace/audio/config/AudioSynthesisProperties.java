package com.aiworkspace.audio.config;

import java.time.Duration;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Builder
@ConfigurationProperties(prefix = "ai-workspace.audio.synthesis")
public record AudioSynthesisProperties(
        Integer maxTextCharacters,
        DataSize maxAudioSize,
        Duration maxAudioDuration,
        Duration requestTimeout,
        Duration connectTimeout,
        Duration readTimeout,
        DataSize maxHeaderSize,
        DataSize maxEventDataSize,
        DataSize maxEventPayloadSize,
        Integer maxEvents,
        Integer maxConcurrentRequests
) {

    public AudioSynthesisProperties {
        maxTextCharacters = maxTextCharacters == null ? 3_000 : maxTextCharacters;
        maxAudioSize = maxAudioSize == null ? DataSize.ofMegabytes(16) : maxAudioSize;
        maxAudioDuration = maxAudioDuration == null ? Duration.ofMinutes(5) : maxAudioDuration;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(90) : requestTimeout;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        maxHeaderSize = maxHeaderSize == null ? DataSize.ofKilobytes(8) : maxHeaderSize;
        maxEventDataSize = maxEventDataSize == null ? DataSize.ofKilobytes(64) : maxEventDataSize;
        maxEventPayloadSize = maxEventPayloadSize == null ? DataSize.ofMegabytes(1) : maxEventPayloadSize;
        maxEvents = maxEvents == null ? 10_000 : maxEvents;
        maxConcurrentRequests = maxConcurrentRequests == null ? 1 : maxConcurrentRequests;

        if (maxTextCharacters <= 0 || maxEvents <= 0 || maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("Synthesis character, event, and concurrency limits must be positive");
        }
        validateSize("max-audio-size", maxAudioSize, Integer.MAX_VALUE - 44L);
        validateSize("max-header-size", maxHeaderSize, Integer.MAX_VALUE);
        validateSize("max-event-data-size", maxEventDataSize, Integer.MAX_VALUE);
        validateSize("max-event-payload-size", maxEventPayloadSize, Integer.MAX_VALUE);
        validateDuration("max-audio-duration", maxAudioDuration);
        validateDuration("request-timeout", requestTimeout);
        validateDuration("connect-timeout", connectTimeout);
        validateDuration("read-timeout", readTimeout);
    }

    private static void validateSize(String name, DataSize size, long maximumBytes) {
        if (size.toBytes() <= 0 || size.toBytes() > maximumBytes) {
            throw new IllegalArgumentException("Synthesis " + name + " must be between 1 and " + maximumBytes + " bytes");
        }
    }

    private static void validateDuration(String name, Duration duration) {
        if (duration.compareTo(Duration.ofMillis(1)) < 0
                || duration.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Synthesis " + name + " must be between 1 and "
                    + Integer.MAX_VALUE + " milliseconds");
        }
    }
}
