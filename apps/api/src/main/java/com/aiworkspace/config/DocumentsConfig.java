package com.aiworkspace.config;

import com.aiworkspace.documents.TextDocumentParser;
import com.aiworkspace.documents.WebPageTextExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsConfig {

    @Bean
    public TextDocumentParser textDocumentParser() {
        return new TextDocumentParser();
    }

    @Bean
    public WebPageTextExtractor webPageTextExtractor() {
        return new WebPageTextExtractor();
    }
}
