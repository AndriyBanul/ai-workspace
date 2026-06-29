package com.aiworkspace.config;

import com.aiworkspace.documents.client.WebPageRestClient;
import com.aiworkspace.documents.services.TextDocumentParser;
import com.aiworkspace.documents.services.WebPageTextExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsConfig {

    @Bean
    public TextDocumentParser textDocumentParser() {
        return new TextDocumentParser();
    }

    @Bean
    public WebPageRestClient webPageRestClient() {
        return new WebPageRestClient();
    }

    @Bean
    public WebPageTextExtractor webPageTextExtractor(WebPageRestClient webPageRestClient) {
        return new WebPageTextExtractor(webPageRestClient);
    }
}
