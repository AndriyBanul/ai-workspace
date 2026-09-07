package com.aiworkspace.documents.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DocumentExtractionProperties.class, WebPageFetchProperties.class})
public class DocumentConfig {
}
