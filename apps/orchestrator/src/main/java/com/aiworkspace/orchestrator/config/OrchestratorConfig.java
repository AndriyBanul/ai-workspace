package com.aiworkspace.orchestrator.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService orchestratorExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public Executor orchestratorTaskExecutor(ExecutorService orchestratorExecutor) {
        return orchestratorExecutor;
    }
}
