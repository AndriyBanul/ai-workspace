package com.aiworkspace.config;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.aiworkspace.knowledge.interfaces.EmbeddingTextSplitter;
import com.aiworkspace.knowledge.services.TokenWindowSplitter;
import java.io.IOException;
import java.util.Map;
import org.springframework.ai.transformers.ResourceCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "transformers", matchIfMissing = true)
public class EmbeddingWindowConfig {
    @Bean(destroyMethod = "close")
    HuggingFaceTokenizer embeddingWindowTokenizer(
            @Value("${spring.ai.embedding.transformer.tokenizer.uri}") String uri,
            @Value("${spring.ai.embedding.transformer.cache.directory}") String cache) throws IOException {
        try (var input = new ResourceCacheService(cache).getCachedResource(uri).getInputStream()) {
            return HuggingFaceTokenizer.newInstance(input, Map.of("padding", "false", "truncation", "false"));
        }
    }

    @Bean
    EmbeddingTextSplitter embeddingTextSplitter(HuggingFaceTokenizer embeddingWindowTokenizer) {
        return new TokenWindowSplitter(text -> embeddingWindowTokenizer.encode(text).getIds().length, 512);
    }
}
