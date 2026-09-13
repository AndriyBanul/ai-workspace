package com.aiworkspace.knowledge.client;

import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.interfaces.EmbeddingTextSplitter;
import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SpringAiTextEmbeddingClient implements TextEmbeddingProvider {

    private static final String PASSAGE_PREFIX = "passage: ";
    private static final String QUERY_PREFIX = "query: ";

    private EmbeddingTextSplitter splitter = (prefix, text) -> List.of(prefix + text);
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private SearchTelemetry telemetry = SearchTelemetry.NOOP;
    private final KnowledgeEmbeddingProperties properties;

    public SpringAiTextEmbeddingClient(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            KnowledgeEmbeddingProperties properties
    ) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.properties = properties;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SpringAiTextEmbeddingClient(ObjectProvider<EmbeddingModel> modelProvider,
            KnowledgeEmbeddingProperties properties, SearchTelemetry telemetry,
            ObjectProvider<EmbeddingTextSplitter> splitterProvider) {
        this(modelProvider, properties);
        this.telemetry = telemetry;
        this.splitter = splitterProvider.getIfAvailable(() -> splitter);
    }

    @Override
    public boolean isConfigured() {
        return properties.enabled() && embeddingModelProvider.getIfAvailable() != null;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    @Override
    public List<List<Float>> embedDocuments(List<String> texts) throws IOException {
        return telemetry.measure("embedding.documents", () -> embedDocumentBatch(texts));
    }

    private List<List<Float>> embedDocumentBatch(List<String> texts) throws IOException {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        var windows = new ArrayList<String>();
        var counts = new ArrayList<Integer>();
        for (String text : texts) {
            var parts = splitter.split(PASSAGE_PREFIX, text);
            windows.addAll(parts);
            counts.add(parts.size());
        }
        var vectors = embedBatches(windows);
        var result = new ArrayList<List<Float>>();
        int offset = 0;
        for (int count : counts) {
            result.add(combine(vectors.subList(offset, offset + count)));
            offset += count;
        }
        return List.copyOf(result);
    }

    @Override
    public List<Float> embedQuery(String text) throws IOException {
        return telemetry.measure("embedding.query", () -> combine(embedBatches(splitter.split(QUERY_PREFIX, text))));
    }

    private List<List<Float>> embedBatches(List<String> windows) {
        var result = new ArrayList<List<Float>>();
        for (int start = 0; start < windows.size(); start += properties.batchSize()) {
            result.addAll(embed(windows.subList(start, Math.min(windows.size(), start + properties.batchSize()))));
        }
        return result;
    }

    private List<Float> combine(List<List<Float>> windows) {
        if (windows.size() == 1) return windows.getFirst();
        float[] mean = new float[properties.dimensions()];
        for (var window : windows) {
            for (int i = 0; i < mean.length; i++) mean[i] += window.get(i) / windows.size();
        }
        return validatedNormalizedVector(mean);
    }

    private List<List<Float>> embed(List<String> texts) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (!properties.enabled() || embeddingModel == null) {
            throw new IllegalStateException("Local embedding model is not configured");
        }

        List<float[]> embeddings = embeddingModel.embed(texts);
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException("Local embedding model returned an unexpected number of vectors");
        }
        return embeddings.stream().map(this::validatedNormalizedVector).toList();
    }

    private List<Float> validatedNormalizedVector(float[] embedding) {
        if (embedding == null || embedding.length != properties.dimensions()) {
            throw new IllegalStateException(
                    "Local embedding model must return " + properties.dimensions() + " dimensions"
            );
        }

        double squaredNorm = 0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Local embedding model returned a non-finite value");
            }
            squaredNorm += (double) value * value;
        }
        if (squaredNorm == 0) {
            throw new IllegalStateException("Local embedding model returned a zero vector");
        }

        double norm = Math.sqrt(squaredNorm);
        List<Float> normalized = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            normalized.add((float) (value / norm));
        }
        return List.copyOf(normalized);
    }
}
