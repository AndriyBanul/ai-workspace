package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.web.client.RestClient;

public final class OpenSearchKnowledgeClientTestFactory {

    private OpenSearchKnowledgeClientTestFactory() {
    }

    public static OpenSearchKnowledgeClient create(URI baseUri, RestClient client, ObjectMapper mapper) {
        return create(baseUri, client, mapper, new KnowledgeEmbeddingProperties(null, null, null, null, null, null));
    }

    public static OpenSearchKnowledgeClient create(URI baseUri, RestClient client, ObjectMapper mapper,
            KnowledgeEmbeddingProperties properties) {
        return create(baseUri, client, mapper, properties, 2);
    }

    public static OpenSearchKnowledgeClient create(URI baseUri, RestClient client, ObjectMapper mapper,
            KnowledgeEmbeddingProperties properties, int headingWeight) {
        return new OpenSearchKnowledgeClient(baseUri.toString(), client, mapper, properties, headingWeight,
                SearchTelemetry.NOOP, null);
    }
}
