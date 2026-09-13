package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.config.KnowledgeQueryExpansionProperties;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.config.KnowledgeSearchProperties;
import com.aiworkspace.knowledge.interfaces.SearchQueryProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSource;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSourceFile;
import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private static final int RETRIEVAL_LIMIT = 12;
    private static final int SOURCE_SNIPPET_LENGTH = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeAnswerProvider knowledgeAnswerProvider;
    private final WorkspaceService workspaceService;
    private final KnowledgeValidator knowledgeValidator;
    private final TextEmbeddingProvider textEmbeddingProvider;
    private SearchTelemetry telemetry = SearchTelemetry.NOOP;
    private final KnowledgeEmbeddingProperties embeddingProperties;
    private final KnowledgeQueryExpansionProperties queryExpansionProperties;
    private final KnowledgeRerankingProperties rerankingProperties;
    private final SearchQueryProvider searchQueryProvider;
    private final KnowledgeSearchProperties searchProperties;
    private final TextReranker textReranker;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider) {
        this(
                knowledgeRepository,
                knowledgeAnswerProvider,
                null,
                new KnowledgeValidator(),
                null,
                new KnowledgeEmbeddingProperties(false, null, null, null, null, null),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID),
                TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(),
                SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled()
        );
    }

    public KnowledgeService(
            KnowledgeRepository knowledgeRepository,
            KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService,
            KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider,
            KnowledgeEmbeddingProperties embeddingProperties
    ) {
        this(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties,
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID),
                TextReranker.NONE, KnowledgeRerankingProperties.disabled(),
                SearchQueryProvider.NONE, KnowledgeQueryExpansionProperties.disabled());
    }

    public KnowledgeService(
            KnowledgeRepository knowledgeRepository,
            KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService,
            KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider,
            KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties
    ) {
        this(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties, searchProperties,
                TextReranker.NONE, KnowledgeRerankingProperties.disabled(),
                SearchQueryProvider.NONE, KnowledgeQueryExpansionProperties.disabled());
    }

    public KnowledgeService(
            KnowledgeRepository knowledgeRepository,
            KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService,
            KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider,
            KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties,
            TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties,
            SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.knowledgeAnswerProvider = knowledgeAnswerProvider;
        this.workspaceService = workspaceService;
        this.knowledgeValidator = knowledgeValidator;
        this.textEmbeddingProvider = textEmbeddingProvider;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.textReranker = textReranker;
        this.rerankingProperties = rerankingProperties;
        this.searchQueryProvider = searchQueryProvider;
        this.queryExpansionProperties = queryExpansionProperties;
    }

    @Autowired
    public KnowledgeService(KnowledgeRepository repository, KnowledgeAnswerProvider answers,
            WorkspaceService workspaces, KnowledgeValidator validator, TextEmbeddingProvider embeddings,
            KnowledgeEmbeddingProperties properties, KnowledgeSearchProperties searchProperties,
            TextReranker reranker, KnowledgeRerankingProperties rerankingProperties,
            SearchQueryProvider searchQueryProvider, KnowledgeQueryExpansionProperties queryExpansionProperties,
            SearchTelemetry telemetry) {
        this(repository, answers, workspaces, validator, embeddings, properties, searchProperties,
                reranker, rerankingProperties, searchQueryProvider, queryExpansionProperties);
        this.telemetry = telemetry;
    }

    public Optional<WorkspaceKnowledge> findWorkspaceKnowledge(String workspaceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        return knowledgeRepository.findByWorkspaceId(workspaceId.trim());
    }

    public WorkspaceKnowledge getWorkspaceKnowledge(String ownerId, String workspaceId) throws IOException {
        workspaceService.getWorkspace(ownerId, workspaceId);
        return findWorkspaceKnowledge(workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Workspace knowledge was not found"));
    }

    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        knowledgeValidator.validateKnowledgeField(field);
        knowledgeValidator.validateKnowledgeFieldValue(value);
        knowledgeValidator.validateWorkspaceId(workspaceId);
        recordKnowledgeItem(workspaceId, sourceTypeFrom(field), field.fieldName(), null, value, null);
    }

    public void recordDocumentsInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordDocumentsInfo(workspaceId, sourceName, jobId, value, null);
    }

    public void recordDocumentsInfo(
            String workspaceId,
            String sourceName,
            String jobId,
            String value,
            KnowledgeSourceMetadata sourceMetadata
    ) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.DOCUMENT, sourceName, jobId, value, sourceMetadata);
    }

    public void recordDocumentsInfo(
            String workspaceId,
            String sourceName,
            String jobId,
            List<KnowledgeChunk> chunks,
            KnowledgeSourceMetadata sourceMetadata
    ) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Document chunks must not be empty");
        }

        String sourceIdentity = sourceMetadata == null
                ? UUID.randomUUID().toString()
                : normalizedOptionalValue(sourceMetadata.sourceId());
        if (sourceIdentity == null) {
            sourceIdentity = UUID.randomUUID().toString();
        }

        Instant createdAt = Instant.now();
        List<KnowledgeItem> items = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException("Document chunk must not be null");
            }
            knowledgeValidator.validateKnowledgeItemContent(chunk.content());
            String chunkId = sourceIdentity + ":" + chunk.sequence();
            items.add(new KnowledgeItem(
                    chunkId,
                    workspaceId.trim(),
                    KnowledgeSourceType.DOCUMENT,
                    normalizedOptionalValue(sourceName),
                    normalizedOptionalValue(jobId),
                    sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.sourceId()),
                    sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.sourceUrl()),
                    chunk.content().trim(),
                    sourceMetadata == null ? null : sourceMetadata.extractedAt(),
                    sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.parserVersion()),
                    chunkId,
                    chunk.sequence(),
                    normalizedOptionalValue(chunk.heading()),
                    chunk.pageNumber(),
                    chunk.slideNumber(),
                    normalizedOptionalValue(chunk.sheetName()),
                    null,
                    null,
                    null,
                    null,
                    createdAt,
                    chunk.sectionId()
            ));
        }
        List<KnowledgeItem> embeddedItems = withEmbeddings(items);
        String sourceId = sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.sourceId());
        if (sourceId == null) {
            knowledgeRepository.addKnowledgeItems(embeddedItems);
        } else {
            knowledgeRepository.replaceKnowledgeItems(workspaceId.trim(), sourceId, embeddedItems);
        }
    }

    public void recordAudioInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.AUDIO, sourceName, jobId, value, null);
    }

    public void recordVideoInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.VIDEO, sourceName, jobId, value, null);
    }

    public void recordImagesInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.IMAGE, sourceName, jobId, value, null);
    }

    public void deleteSourceKnowledge(String workspaceId, String sourceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeValidator.validateSourceId(sourceId);
        knowledgeRepository.deleteKnowledgeItemsBySourceId(workspaceId.trim(), sourceId.trim());
    }

    public void deleteWorkspaceKnowledge(String workspaceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeRepository.deleteKnowledgeItemsByWorkspaceId(workspaceId.trim());
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(String workspaceId, String question) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeValidator.validateQuestion(question);
        String trimmedWorkspaceId = workspaceId.trim();
        String trimmedQuestion = question.trim();
        List<KnowledgeItem> items = knowledgeRepository.expandNeighbors(trimmedWorkspaceId,
                retrieve(trimmedWorkspaceId, trimmedQuestion));
        if (items.isEmpty()) {
            throw new NoSuchElementException("Workspace knowledge was not found");
        }

        String answer = knowledgeAnswerProvider.answer(trimmedQuestion, contextFrom(trimmedWorkspaceId, items));

        return new WorkspaceKnowledgeAnswer(
                trimmedWorkspaceId,
                trimmedQuestion,
                answer,
                sourceFilesFrom(items),
                items.stream().map(this::sourceFrom).toList()
        );
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(
            String ownerId,
            String workspaceId,
            WorkspaceQuestionRequest request
    ) throws IOException {
        knowledgeValidator.validateQuestionRequest(request);

        workspaceService.getWorkspace(ownerId, workspaceId);
        return answerWorkspaceQuestion(workspaceId, request.question());
    }

    private void recordKnowledgeItem(
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String value,
            KnowledgeSourceMetadata sourceMetadata
    ) throws IOException {
        knowledgeValidator.validateKnowledgeItemContent(value);
        knowledgeValidator.validateWorkspaceId(workspaceId);
        KnowledgeItem item = new KnowledgeItem(
                UUID.randomUUID().toString(),
                workspaceId.trim(),
                sourceType,
                normalizedOptionalValue(sourceName),
                normalizedOptionalValue(jobId),
                sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.sourceId()),
                sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.sourceUrl()),
                value.trim(),
                sourceMetadata == null ? null : sourceMetadata.extractedAt(),
                sourceMetadata == null ? null : normalizedOptionalValue(sourceMetadata.parserVersion()),
                Instant.now()
        );
        knowledgeRepository.addKnowledgeItems(withEmbeddings(List.of(item)));
    }

    private List<KnowledgeItem> retrieve(String workspaceId, String question) throws IOException {
        if (textEmbeddingProvider == null || !textEmbeddingProvider.isConfigured()) {
            telemetry.fallback("unconfigured");
            return knowledgeRepository.searchKnowledgeItems(workspaceId, question, RETRIEVAL_LIMIT);
        }

        List<String> queries = searchQueries(question);
        if (queries.size() > 1) {
            return retrieveExpanded(workspaceId, question, queries);
        }

        int candidateLimit = rerankingEnabled() ? rerankingProperties.candidateLimit() : RETRIEVAL_LIMIT;
        List<KnowledgeItem> candidates;
        String stage = "embedding";
        try {
            List<Float> queryEmbedding = textEmbeddingProvider.embedQuery(question);
            if (searchProperties.vectorOnly()) {
                stage = "vector_search";
                candidates = knowledgeRepository.searchKnowledgeItemsByVector(
                        workspaceId, queryEmbedding, candidateLimit,
                        Math.max(candidateLimit, embeddingProperties.candidateLimit()));
            } else {
                stage = "hybrid_search";
                candidates = knowledgeRepository.searchKnowledgeItems(
                        workspaceId,
                        question,
                        queryEmbedding,
                        candidateLimit,
                        Math.max(candidateLimit, embeddingProperties.candidateLimit()),
                        embeddingProperties.rrfRankConstant()
                );
            }
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback(stage);
            LOGGER.warn(
                    "Retrieval fallback stage={} workspaceId={} exception={}",
                    stage, workspaceId,
                    exception.getClass().getSimpleName()
            );
            return knowledgeRepository.searchKnowledgeItems(workspaceId, question, RETRIEVAL_LIMIT);
        }

        return rerank(question, candidates);
    }

    private List<String> searchQueries(String question) {
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (searchQueryProvider == null
                || !searchQueryProvider.isConfigured()
                || !queryExpansionProperties.enabled()) {
            return List.copyOf(queries);
        }
        try {
            for (String expanded : searchQueryProvider.expand(question, queryExpansionProperties.queryLimit())) {
                if (expanded != null
                        && !expanded.isBlank()
                        && queries.stream().noneMatch(query -> query.equalsIgnoreCase(expanded.trim()))) {
                    queries.add(expanded.trim());
                }
            }
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback("query_expansion");
            LOGGER.warn("Query expansion fallback exception={}", exception.getClass().getSimpleName());
        }
        return List.copyOf(queries);
    }

    private List<KnowledgeItem> retrieveExpanded(String workspaceId, String question, List<String> queries)
            throws IOException {
        int mergedLimit = rerankingEnabled() ? rerankingProperties.candidateLimit() : RETRIEVAL_LIMIT;
        int perQueryLimit = rerankingEnabled() ? rerankingProperties.candidateLimit() : RETRIEVAL_LIMIT;
        List<List<KnowledgeItem>> rankings = new ArrayList<>();
        try {
            for (String query : queries) {
                List<Float> embedding = textEmbeddingProvider.embedQuery(query);
                rankings.add(knowledgeRepository.searchKnowledgeItemsByVector(
                        workspaceId,
                        embedding,
                        perQueryLimit,
                        Math.max(perQueryLimit, embeddingProperties.candidateLimit())
                ));
                if (!searchProperties.vectorOnly()) {
                    rankings.add(knowledgeRepository.searchKnowledgeItems(workspaceId, query, perQueryLimit));
                }
            }
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback("expanded_search");
            LOGGER.warn(
                    "Expanded retrieval fallback workspaceId={} exception={}",
                    workspaceId,
                    exception.getClass().getSimpleName()
            );
            return knowledgeRepository.searchKnowledgeItems(workspaceId, question, RETRIEVAL_LIMIT);
        }
        return rerank(question, interleave(rankings, mergedLimit));
    }

    private List<KnowledgeItem> interleave(List<List<KnowledgeItem>> rankings, int limit) {
        Map<String, KnowledgeItem> merged = new LinkedHashMap<>();
        int rank = 0;
        boolean added;
        do {
            added = false;
            for (List<KnowledgeItem> ranking : rankings) {
                if (rank < ranking.size()) {
                    KnowledgeItem item = ranking.get(rank);
                    merged.putIfAbsent(item.id(), item);
                    added = true;
                    if (merged.size() == limit) {
                        return List.copyOf(merged.values());
                    }
                }
            }
            rank++;
        } while (added);
        return List.copyOf(merged.values());
    }

    private List<KnowledgeItem> rerank(String question, List<KnowledgeItem> candidates) {
        if (!rerankingEnabled()) {
            return candidates.stream().limit(RETRIEVAL_LIMIT).toList();
        }
        try {
            return telemetry.measure("search.rerank", () -> textReranker.rerank(
                    question, candidates, rerankingProperties.resultLimit()));
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback("reranking");
            LOGGER.warn(
                    "Reranking fallback model={} exception={}",
                    rerankingProperties.model(),
                    exception.getClass().getSimpleName()
            );
            return candidates.stream().limit(RETRIEVAL_LIMIT).toList();
        }
    }

    private boolean rerankingEnabled() {
        return textReranker != null && textReranker.isConfigured() && rerankingProperties.enabled();
    }

    private List<KnowledgeItem> withEmbeddings(List<KnowledgeItem> items) throws IOException {
        if (textEmbeddingProvider == null) {
            return items;
        }
        if (!textEmbeddingProvider.isConfigured()) {
            throw new IllegalStateException("Text embedding provider is not configured");
        }

        List<String> inputs = items.stream().map(this::embeddingInput).toList();
        List<List<Float>> embeddings = textEmbeddingProvider.embedDocuments(inputs);
        if (embeddings.size() != items.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected number of vectors");
        }

        List<KnowledgeItem> embeddedItems = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            List<Float> embedding = embeddings.get(index);
            if (embedding.size() != textEmbeddingProvider.dimensions()) {
                throw new IllegalStateException("Embedding provider returned a vector with unexpected dimensions");
            }
            embeddedItems.add(new KnowledgeItem(
                    item.id(),
                    item.workspaceId(),
                    item.sourceType(),
                    item.sourceName(),
                    item.jobId(),
                    item.sourceId(),
                    item.sourceUrl(),
                    item.content(),
                    item.extractedAt(),
                    item.parserVersion(),
                    item.chunkId(),
                    item.chunkSequence(),
                    item.heading(),
                    item.pageNumber(),
                    item.slideNumber(),
                    item.sheetName(),
                    List.copyOf(embedding),
                    textEmbeddingProvider.model(),
                    textEmbeddingProvider.dimensions(),
                    sha256(inputs.get(index)),
                    item.createdAt(),
                    item.sectionId()
            ));
        }
        return List.copyOf(embeddedItems);
    }

    private String embeddingInput(KnowledgeItem item) {
        if (item.heading() == null || item.heading().isBlank()) {
            return item.content();
        }
        return item.heading() + "\n\n" + item.content();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private KnowledgeSourceType sourceTypeFrom(WorkspaceKnowledgeField field) {
        return switch (field) {
            case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
            case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
            case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
            case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
        };
    }

    private String contextFrom(String workspaceId, List<KnowledgeItem> items) {
        StringBuilder context = new StringBuilder("Workspace ID: ")
                .append(workspaceId)
                .append("\n\nRelevant source files and evidence:\n");

        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            context.append("\n[")
                    .append(index + 1)
                    .append("] Source file: ")
                    .append(sourceDisplayName(item))
                    .append("\nSource file key: ")
                    .append(sourceFileKey(item))
                    .append("\nType: ")
                    .append(item.sourceType().apiName())
                    .append("\nJob ID: ")
                    .append(valueOrEmpty(item.jobId()))
                    .append("\nSource ID: ")
                    .append(valueOrEmpty(item.sourceId()))
                    .append("\nSource URL: ")
                    .append(valueOrEmpty(item.sourceUrl()))
                    .append("\nExtracted at: ")
                    .append(item.extractedAt() == null ? "(empty)" : item.extractedAt())
                    .append("\nParser version: ")
                    .append(valueOrEmpty(item.parserVersion()))
                    .append("\nChunk ID: ")
                    .append(valueOrEmpty(item.chunkId()))
                    .append("\nChunk sequence: ")
                    .append(item.chunkSequence() == null ? "(empty)" : item.chunkSequence())
                    .append("\nHeading: ")
                    .append(valueOrEmpty(item.heading()))
                    .append("\nLocation: ")
                    .append(location(item))
                    .append("\nContent:\n")
                    .append(item.content())
                    .append("\n");
        }

        return context.toString();
    }

    private WorkspaceKnowledgeSource sourceFrom(KnowledgeItem item) {
        return new WorkspaceKnowledgeSource(
                item.id(),
                item.sourceType().apiName(),
                item.sourceName(),
                item.jobId(),
                item.sourceId(),
                item.sourceUrl(),
                item.extractedAt(),
                item.parserVersion(),
                item.chunkId(),
                item.chunkSequence(),
                item.heading(),
                item.pageNumber(),
                item.slideNumber(),
                item.sheetName(),
                snippet(item.content()),
                sourceFileKey(item)
        );
    }

    private List<WorkspaceKnowledgeSourceFile> sourceFilesFrom(List<KnowledgeItem> items) {
        Map<String, SourceFileAccumulator> sourceFiles = new LinkedHashMap<>();
        for (KnowledgeItem item : items) {
            String sourceFileKey = sourceFileKey(item);
            sourceFiles.computeIfAbsent(
                    sourceFileKey,
                    key -> new SourceFileAccumulator(
                            key,
                            sourceDisplayName(item),
                            item.sourceType().apiName(),
                            item.jobId(),
                            item.sourceId(),
                            item.sourceUrl(),
                            item.extractedAt(),
                            item.parserVersion()
                    )
            ).increment();
        }

        return sourceFiles.values().stream()
                .map(SourceFileAccumulator::toSourceFile)
                .toList();
    }

    private String sourceFileKey(KnowledgeItem item) {
        if (item.sourceId() != null && !item.sourceId().isBlank()) {
            return item.sourceType().apiName() + ":" + item.sourceId();
        }
        return item.sourceType().apiName()
                + ":"
                + nullToEmpty(item.sourceName())
                + ":"
                + nullToEmpty(item.jobId());
    }

    private String sourceDisplayName(KnowledgeItem item) {
        if (item.sourceName() == null || item.sourceName().isBlank()) {
            return item.sourceType().apiName();
        }

        return item.sourceName();
    }

    private String location(KnowledgeItem item) {
        if (item.pageNumber() != null) {
            return "page " + item.pageNumber();
        }
        if (item.slideNumber() != null) {
            return "slide " + item.slideNumber();
        }
        if (item.sheetName() != null && !item.sheetName().isBlank()) {
            return "sheet " + item.sheetName();
        }
        return "(empty)";
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }

        if (value.length() <= SOURCE_SNIPPET_LENGTH) {
            return value;
        }

        return value.substring(0, SOURCE_SNIPPET_LENGTH);
    }

    private String normalizedOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String valueOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }

        return value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static class SourceFileAccumulator {

        private final String key;
        private final String name;
        private final String type;
        private final String jobId;
        private final String sourceId;
        private final String sourceUrl;
        private final Instant extractedAt;
        private final String parserVersion;
        private int sourceCount;

        SourceFileAccumulator(
                String key,
                String name,
                String type,
                String jobId,
                String sourceId,
                String sourceUrl,
                Instant extractedAt,
                String parserVersion
        ) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.jobId = jobId;
            this.sourceId = sourceId;
            this.sourceUrl = sourceUrl;
            this.extractedAt = extractedAt;
            this.parserVersion = parserVersion;
        }

        void increment() {
            sourceCount++;
        }

        WorkspaceKnowledgeSourceFile toSourceFile() {
            return new WorkspaceKnowledgeSourceFile(
                    key,
                    name,
                    type,
                    jobId,
                    sourceId,
                    sourceUrl,
                    extractedAt,
                    parserVersion,
                    sourceCount
            );
        }
    }
}
