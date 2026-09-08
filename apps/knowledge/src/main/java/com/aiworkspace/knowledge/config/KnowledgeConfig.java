package com.aiworkspace.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KnowledgeEmbeddingProperties.class)
public class KnowledgeConfig {
}
