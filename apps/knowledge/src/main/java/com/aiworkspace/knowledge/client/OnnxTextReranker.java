package com.aiworkspace.knowledge.client;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.transformers.ResourceCacheService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class OnnxTextReranker implements TextReranker, DisposableBean {

    private final KnowledgeRerankingProperties properties;
    private final Object initializationLock = new Object();
    private final Object inferenceLock = new Object();
    private volatile Runtime runtime;

    public OnnxTextReranker(KnowledgeRerankingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.enabled();
    }

    @Override
    public List<KnowledgeItem> rerank(String query, List<KnowledgeItem> candidates, int limit) throws IOException {
        if (!isConfigured() || candidates.size() < 2) {
            return candidates.stream().limit(limit).toList();
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Reranking query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Reranking limit must be positive");
        }

        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        Runtime activeRuntime = runtime();
        synchronized (inferenceLock) {
            for (int from = 0; from < candidates.size(); from += properties.batchSize()) {
                int to = Math.min(from + properties.batchSize(), candidates.size());
                List<Double> scores = score(activeRuntime, query, candidates.subList(from, to));
                for (int index = 0; index < scores.size(); index++) {
                    scored.add(new ScoredCandidate(candidates.get(from + index), scores.get(index), from + index));
                }
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparingInt(ScoredCandidate::originalRank))
                .limit(Math.min(limit, candidates.size()))
                .map(ScoredCandidate::item)
                .toList();
    }

    private List<Double> score(Runtime activeRuntime, String query, List<KnowledgeItem> candidates)
            throws IOException {
        PairList<String, String> pairs = new PairList<>(candidates.size());
        for (KnowledgeItem candidate : candidates) {
            pairs.add(query, passage(candidate));
        }
        Encoding[] encodings = activeRuntime.tokenizer().batchEncode(pairs);
        long[][] inputIds = arrays(encodings, Encoding::getIds);
        long[][] attentionMask = arrays(encodings, Encoding::getAttentionMask);
        long[][] tokenTypeIds = arrays(encodings, Encoding::getTypeIds);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            inputs.put("input_ids", OnnxTensor.createTensor(environment, inputIds));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, attentionMask));
            if (activeRuntime.session().getInputNames().contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, tokenTypeIds));
            }
            try (OrtSession.Result result = activeRuntime.session().run(inputs)) {
                return scores(result.get(0).getValue(), candidates.size());
            }
        } catch (OrtException exception) {
            throw new IOException("Local reranking inference failed", exception);
        } finally {
            OnnxValue.close(inputs.values());
        }
    }

    private long[][] arrays(Encoding[] encodings, EncodingValues values) {
        long[][] result = new long[encodings.length][];
        for (int index = 0; index < encodings.length; index++) {
            result[index] = values.get(encodings[index]);
        }
        return result;
    }

    private List<Double> scores(Object output, int expectedSize) throws IOException {
        List<Double> scores = new ArrayList<>(expectedSize);
        if (output instanceof float[][] values) {
            for (float[] value : values) {
                if (value.length == 0) {
                    throw new IOException("Local reranker returned an empty score");
                }
                scores.add((double) value[0]);
            }
        } else if (output instanceof double[][] values) {
            for (double[] value : values) {
                if (value.length == 0) {
                    throw new IOException("Local reranker returned an empty score");
                }
                scores.add(value[0]);
            }
        } else {
            throw new IOException("Local reranker returned an unsupported output type");
        }
        if (scores.size() != expectedSize || scores.stream().anyMatch(score -> !Double.isFinite(score))) {
            throw new IOException("Local reranker returned invalid scores");
        }
        return List.copyOf(scores);
    }

    private Runtime runtime() throws IOException {
        Runtime current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (initializationLock) {
            if (runtime == null) {
                runtime = loadRuntime();
            }
            return runtime;
        }
    }

    private Runtime loadRuntime() throws IOException {
        ResourceCacheService cache = new ResourceCacheService(properties.cacheDirectory());
        try {
            var model = cache.getCachedResource(properties.modelUri());
            var tokenizerResource = cache.getCachedResource(properties.tokenizerUri());
            HuggingFaceTokenizer tokenizer;
            try (InputStream input = tokenizerResource.getInputStream()) {
                tokenizer = HuggingFaceTokenizer.newInstance(input, Map.of(
                        "padding", "true",
                        "truncation", "true",
                        "maxLength", Integer.toString(properties.maxTokens())
                ));
            }
            try {
                OrtSession session = OrtEnvironment.getEnvironment().createSession(model.getFile().getAbsolutePath());
                return new Runtime(tokenizer, session);
            } catch (IOException | OrtException exception) {
                tokenizer.close();
                throw exception;
            }
        } catch (OrtException | RuntimeException exception) {
            throw new IOException("Failed to initialize local reranker " + properties.model(), exception);
        }
    }

    private String passage(KnowledgeItem item) {
        if (item.heading() == null || item.heading().isBlank()) {
            return item.content();
        }
        return item.heading() + "\n\n" + item.content();
    }

    @Override
    public void destroy() throws Exception {
        Runtime current = runtime;
        runtime = null;
        if (current != null) {
            current.session().close();
            current.tokenizer().close();
        }
    }

    private record Runtime(HuggingFaceTokenizer tokenizer, OrtSession session) {
    }

    private record ScoredCandidate(KnowledgeItem item, double score, int originalRank) {
    }

    @FunctionalInterface
    private interface EncodingValues {
        long[] get(Encoding encoding);
    }
}
