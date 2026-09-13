package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpringAiTextEmbeddingClientTest {

    @Test
    void prefixesBatchesAndNormalizesLocalDocumentEmbeddings() throws Exception {
        CapturingEmbeddingModel model = new CapturingEmbeddingModel();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", model);
        SpringAiTextEmbeddingClient client = new SpringAiTextEmbeddingClient(
                beans.getBeanProvider(EmbeddingModel.class),
                new KnowledgeEmbeddingProperties(true, "local-model", 3, 2, 32, 60)
        );

        List<List<Float>> embeddings = client.embedDocuments(List.of("first", "second", "third"));

        assertEquals(
                List.of(List.of("passage: first", "passage: second"), List.of("passage: third")),
                model.batches
        );
        assertEquals(List.of(0.6f, 0.8f, 0.0f), embeddings.getFirst());
    }

    @Test
    void prefixesQueriesForAsymmetricRetrieval() throws Exception {
        CapturingEmbeddingModel model = new CapturingEmbeddingModel();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", model);
        SpringAiTextEmbeddingClient client = new SpringAiTextEmbeddingClient(
                beans.getBeanProvider(EmbeddingModel.class),
                new KnowledgeEmbeddingProperties(true, "local-model", 3, 2, 32, 60)
        );

        client.embedQuery("What grew?");

        assertEquals(List.of("query: What grew?"), model.batches.getFirst());
    }

    @Test
    void reportsMissingLocalModel() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        SpringAiTextEmbeddingClient client = new SpringAiTextEmbeddingClient(
                beans.getBeanProvider(EmbeddingModel.class),
                new KnowledgeEmbeddingProperties(true, "local-model", 3, 2, 32, 60)
        );

        assertFalse(client.isConfigured());
    }

    @Test
    void poolsAllWindowsIntoOneVectorPerStoredChunk() throws Exception {
        var model = new CapturingEmbeddingModel();
        var beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", model);
        beans.addBean("splitter", new com.aiworkspace.knowledge.services.TokenWindowSplitter(String::length, 12));
        var client = new SpringAiTextEmbeddingClient(beans.getBeanProvider(EmbeddingModel.class),
                new KnowledgeEmbeddingProperties(true, "test", 3, 2, 32, 60),
                com.aiworkspace.knowledge.observability.SearchTelemetry.NOOP,
                beans.getBeanProvider(com.aiworkspace.knowledge.interfaces.EmbeddingTextSplitter.class));
        var result = client.embedDocuments(List.of("A very long sentence which stays intact in storage."));
        assertEquals(1, result.size());
        assertEquals(0.6f, result.getFirst().getFirst(), 0.00001);
        assertEquals("A very long sentence which stays intact in storage.",
                model.batches.stream().flatMap(List::stream).map(t -> t.substring(9)).reduce("", String::concat));
    }

    private static class CapturingEmbeddingModel implements EmbeddingModel {

        private final List<List<String>> batches = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> texts) {
            batches.add(List.copyOf(texts));
            return texts.stream().map(ignored -> new float[] {3, 4, 0}).toList();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException();
        }
    }
}
