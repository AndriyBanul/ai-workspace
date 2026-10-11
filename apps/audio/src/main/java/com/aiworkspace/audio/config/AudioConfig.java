package com.aiworkspace.audio.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioSynthesisProperties.class)
public class AudioConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService piperDeadlineScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon().name("piper-deadline-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
