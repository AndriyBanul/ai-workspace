package com.aiworkspace.knowledge.client;

import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import com.aiworkspace.documents.services.DocumentChunker;
import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.models.KnowledgeChunkMetadata;
import com.aiworkspace.knowledge.models.KnowledgeEmbeddingMetadata;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeItemSource;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

@Tag("retrieval-evaluation")
class RetrievalEvaluationTest {
    private static final String URL = "http://localhost:19200";
    private static final String INDEX = "/knowledge-items-v3";
    private static final Config BASELINE = new Config(5, 2, 32, 60);
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient http = RestClient.create(URL);
    private final KnowledgeEmbeddingProperties properties =
            new KnowledgeEmbeddingProperties(true, null, 768, 8, 32, 60);

    record Corpus(int version, List<Source> documents, List<Question> questions) {}
    record Source(String id, String heading, List<String> paragraphs) {}
    record Question(String id, String split, String question, String sourceId, String evidence) {}
    record Config(int chunkSentences, int headingWeight, int candidates, int rrf) {}
    record Outcome(String questionId, int firstRelevantRank, List<String> expectedChunks, List<String> retrievedChunks, List<String> contextChunks, boolean contextHit) {}
    record Run(Config config, String mode, String split, double hit1, double hit3, double hit5, double contextHit, List<Outcome> outcomes) {}

    @Test
    void evaluateRetrieval() throws Exception {
        var cluster = json.readTree(http.get().uri("/").retrieve().body(String.class));
        assertEquals("ai-workspace-integration-test", cluster.path("cluster_name").asText(),
                "Refusing to modify anything except the isolated test cluster");
        Corpus corpus;
        try (var input = getClass().getResourceAsStream("/retrieval/corpus.json")) {
            assertNotNull(input);
            corpus = json.readValue(input, Corpus.class);
        }
        for (var question : corpus.questions()) {
            assertTrue(corpus.documents().stream().anyMatch(source -> source.id().equals(question.sourceId())
                    && source.paragraphs().stream().anyMatch(text -> text.contains(question.evidence()))),
                    "Invalid source passage label: " + question.id());
        }
        var output = Path.of(System.getProperty("evaluation.output"));
        Files.createDirectories(output);
        var configProperties = new Properties();
        try (var input = Files.newInputStream(Path.of(System.getProperty("evaluation.applicationProperties")))) {
            configProperties.load(input);
        }

        try (var tokenizerInput = new org.springframework.ai.transformers.ResourceCacheService(
                    System.getenv().getOrDefault("KNOWLEDGE_EMBEDDING_CACHE_DIRECTORY", "data/models/spring-ai"))
                    .getCachedResource(defaultValue(configProperties, "spring.ai.embedding.transformer.tokenizer.uri")).getInputStream();
             var tokenizer = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.newInstance(tokenizerInput,
                    Map.of("padding", "false", "truncation", "false"));
             var model = new TransformersEmbeddingModel()) {
            model.setModelResource(defaultValue(configProperties, "spring.ai.embedding.transformer.onnx.model-uri"));
            model.setTokenizerResource(defaultValue(configProperties, "spring.ai.embedding.transformer.tokenizer.uri"));
            model.setTokenizerOptions(Map.of("padding", "true", "truncation", "false", "maxLength", "512"));
            model.setResourceCacheDirectory(System.getenv().getOrDefault(
                    "KNOWLEDGE_EMBEDDING_CACHE_DIRECTORY", "data/models/spring-ai"));
            model.afterPropertiesSet();
            var beans = new StaticListableBeanFactory();
            beans.addBean("embeddingModel", model);
            beans.addBean("splitter", new com.aiworkspace.knowledge.services.TokenWindowSplitter(
                    text -> tokenizer.encode(text).getIds().length, 512));
            var embeddings = new SpringAiTextEmbeddingClient(beans.getBeanProvider(EmbeddingModel.class), properties,
                    com.aiworkspace.knowledge.observability.SearchTelemetry.NOOP,
                    beans.getBeanProvider(com.aiworkspace.knowledge.interfaces.EmbeddingTextSplitter.class));
            var vectors = new HashMap<String, List<Float>>();
            for (var question : corpus.questions()) {
                vectors.put(question.id(), embeddings.embedQuery(question.question()));
            }
            var indexed = new HashMap<Integer, List<KnowledgeItem>>();
            var runs = new ArrayList<Run>();
            for (int size : List.of(5, 3, 8)) {
                var items = embedChunks(corpus, size, embeddings);
                indexed.put(size, items);
                index(items);
                for (int weight : List.of(2, 1, 4)) {
                    var client = client(weight);
                    runs.add(evaluate(new Config(size, weight, 32, 60), "BM25", "tune",
                            corpus, items, vectors, client));
                    for (int candidates : List.of(32, 16, 8)) {
                        for (int rrf : List.of(60, 10)) {
                            runs.add(evaluate(new Config(size, weight, candidates, rrf), "hybrid", "tune",
                                    corpus, items, vectors, client));
                        }
                    }
                }
                System.out.println("Evaluated chunk size " + size + " with " + items.size() + " chunks");
            }
            // Stable ordering favors the current defaults when tuning scores tie.
            Run selected = runs.stream().filter(run -> run.mode().equals("hybrid"))
                    .sorted(Comparator.comparingDouble(Run::hit1).reversed()
                            .thenComparing(Comparator.comparingDouble(Run::hit3).reversed())
                            .thenComparing(Comparator.comparingDouble(Run::hit5).reversed()))
                    .findFirst().orElseThrow();
            var validation = new ArrayList<Run>();
            for (var config : new LinkedHashSet<>(List.of(BASELINE, selected.config()))) {
                var items = indexed.get(config.chunkSentences());
                index(items);
                for (var mode : List.of("BM25", "hybrid")) {
                    validation.add(evaluate(config, mode, "holdout", corpus, items, vectors, client(config.headingWeight())));
                }
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("corpusVersion", corpus.version());
            report.put("corpusSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(corpus))));
            report.put("openSearchVersion", cluster.path("version").path("number").asText());
            report.put("modelUri", defaultValue(configProperties, "spring.ai.embedding.transformer.onnx.model-uri"));
            report.put("baseline", BASELINE);
            report.put("selectedOnTuneOnly", selected.config());
            report.put("tuning", runs);
            report.put("holdout", validation);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(), report);
            var markdown = new StringBuilder("# Retrieval evaluation\n\n")
                    .append("Synthetic corpus: 16 documents, 12 tuning questions and 12 held-out questions.\n\n")
                    .append("Metrics are hit rate: fraction of questions with at least one labelled chunk in the top K. ")
                    .append("This is not a production quality guarantee or recall over all relevant chunks.\n\n")
                    .append("Selected using tuning questions only: ").append(selected.config()).append("\n\n")
                    .append("| Split | Mode | Chunk sentences | Heading weight | Candidates | RRF | Hit@1 | Hit@3 | Hit@5 | Context hit |\n")
                    .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (var run : runs) appendRow(markdown, run);
            for (var run : validation) appendRow(markdown, run);
            markdown.append("\nSee results.json for question-level ranks, expected chunk IDs and retrieved IDs.\n");
            Files.writeString(output.resolve("summary.md"), markdown);
            assertTrue(validation.stream().filter(run -> run.mode().equals("hybrid"))
                    .allMatch(run -> run.contextHit() >= 0.75), "Hybrid held-out expanded-context coverage fell below the initial 75% smoke gate; inspect results.json");
        }
    }

    private String defaultValue(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value.substring(value.indexOf(':') + 1, value.length() - 1);
    }

    private List<KnowledgeItem> embedChunks(Corpus corpus, int size, SpringAiTextEmbeddingClient embeddings) throws Exception {
        var result = new ArrayList<KnowledgeItem>();
        var chunker = new DocumentChunker();
        for (var source : corpus.documents()) {
            var blocks = new ArrayList<DocumentTextBlock>();
            blocks.add(new DocumentTextBlock(1, DocumentBlockType.HEADING, source.heading(), null, null, null));
            for (var paragraph : source.paragraphs()) {
                blocks.add(new DocumentTextBlock(blocks.size() + 1, DocumentBlockType.PARAGRAPH, paragraph, null, null, null));
            }
            var chunks = chunker.chunk(blocks, "", size);
            var vectors = embeddings.embedDocuments(chunks.stream()
                    .map(chunk -> chunk.heading() + "\n\n" + chunk.content()).toList());
            for (int index = 0; index < chunks.size(); index++) {
                var chunk = chunks.get(index);
                String id = source.id() + ":" + chunk.sequence();
                result.add(KnowledgeItem.builder().id(id).workspaceId("evaluation")
                        .source(KnowledgeItemSource.builder().id(source.id()).name(source.heading())
                                .type(KnowledgeSourceType.DOCUMENT).build())
                        .content(chunk.content())
                        .chunkMetadata(KnowledgeChunkMetadata.builder().id(id).heading(chunk.heading())
                                .sequence(chunk.sequence()).sectionId(chunk.sectionId()).build())
                        .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                                vectors.get(index), embeddings.model(), 768, null))
                        .createdAt(Instant.parse("2026-01-01T00:00:00Z")).build());
            }
        }
        return result;
    }

    private void index(List<KnowledgeItem> items) throws Exception {
        http.delete().uri(INDEX).retrieve().onStatus(status -> status.value() == 404,
                (request, response) -> {}).toBodilessEntity();
        client(2).addKnowledgeItems(items);
    }

    private OpenSearchKnowledgeClient client(int weight) {
        return new OpenSearchKnowledgeClient(URI.create(URL), RestClient.create(), json, properties, weight);
    }

    private Run evaluate(Config config, String mode, String split, Corpus corpus, List<KnowledgeItem> items,
                         Map<String, List<Float>> vectors, OpenSearchKnowledgeClient client) throws Exception {
        var outcomes = new ArrayList<Outcome>();
        for (var question : corpus.questions()) {
            if (!question.split().equals(split)) continue;
            var relevant = items.stream().filter(item -> item.sourceId().equals(question.sourceId())
                    && item.content().contains(question.evidence())).map(KnowledgeItem::id).toList();
            // If chunking splits the labelled answer, count a miss rather than hiding the loss.

            var results = mode.equals("BM25")
                    ? client.searchKnowledgeItems("evaluation", question.question(), 12)
                    : client.searchKnowledgeItems("evaluation", question.question(), vectors.get(question.id()),
                            12, config.candidates(), config.rrf());
            int rank = 0;
            for (int i = 0; i < results.size(); i++) {
                if (relevant.contains(results.get(i).id())) { rank = i + 1; break; }
            }
            var context = client.expandNeighbors("evaluation", results);
            outcomes.add(new Outcome(question.id(), rank, relevant, results.stream().map(KnowledgeItem::id).toList(),
                    context.stream().map(KnowledgeItem::id).toList(),
                    context.stream().anyMatch(item -> relevant.contains(item.id()))));
        }
        return new Run(config, mode, split, hit(outcomes, 1), hit(outcomes, 3), hit(outcomes, 5), outcomes.stream().filter(Outcome::contextHit).count() / (double) outcomes.size(), outcomes);
    }

    private double hit(List<Outcome> outcomes, int k) {
        return outcomes.stream().filter(o -> o.firstRelevantRank() > 0 && o.firstRelevantRank() <= k).count()
                / (double) outcomes.size();
    }

    private void appendRow(StringBuilder text, Run run) {
        var c = run.config();
        text.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d | %d | %.1f%% | %.1f%% | %.1f%% | %.1f%% |%n",
                run.split(), run.mode(), c.chunkSentences(), c.headingWeight(), c.candidates(), c.rrf(),
                100 * run.hit1(), 100 * run.hit3(), 100 * run.hit5(), 100 * run.contextHit()));
    }
}
