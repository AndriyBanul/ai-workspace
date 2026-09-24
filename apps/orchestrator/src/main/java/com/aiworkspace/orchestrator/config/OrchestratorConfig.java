package com.aiworkspace.orchestrator.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.aiworkspace.orchestrator.services.IngestionTelemetry;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SourceRecoveryProperties.class, IngestionExecutorProperties.class})
public class OrchestratorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService orchestratorExecutor(IngestionExecutorProperties properties,
            IngestionTelemetry telemetry) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(properties.threads(), properties.threads(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()), new ThreadPoolExecutor.AbortPolicy());
        telemetry.registerWorkerQueue(executor);
        return executor;
    }

    @Bean
    public Executor orchestratorTaskExecutor(@Qualifier("orchestratorExecutor") ExecutorService orchestratorExecutor) {
        return orchestratorExecutor;
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService sourceLeaseScheduler() {
        return Executors.newScheduledThreadPool(2);
    }
}
