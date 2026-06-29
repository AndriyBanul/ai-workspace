package com.aiworkspace.config;

import com.aiworkspace.documents.client.RestClient;
import com.aiworkspace.documents.services.TextDocumentParserService;
import com.aiworkspace.documents.services.WebPageTextExtractorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsConfig {

    @Bean
    public TextDocumentParserService textDocumentParserService() {
        return new TextDocumentParserService();
    }

    @Bean
    public RestClient restClient() {
        return new RestClient();
    }

    @Bean
    public WebPageTextExtractorService webPageTextExtractorService(RestClient restClient) {
        return new WebPageTextExtractorService(restClient);
    }
}
