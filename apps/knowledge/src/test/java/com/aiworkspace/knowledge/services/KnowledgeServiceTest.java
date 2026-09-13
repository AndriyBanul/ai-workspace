package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.config.KnowledgeQueryExpansionProperties;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.config.KnowledgeSearchProperties;
import com.aiworkspace.knowledge.interfaces.SearchQueryProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceTest {

    @Test
    void storesOneVersionedEmbeddingForEachDocumentChunk() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = embeddingService(repository);

        service.recordDocumentsInfo(
                "workspace-1",
                "report.pdf",
                "job-1",
                List.of(
                        new KnowledgeChunk(1, "Revenue increased", "Revenue", 2, null, null),
                        new KnowledgeChunk(2, "Costs decreased", "Costs", 3, null, null)
                ),
                new KnowledgeSourceMetadata("file-42", null, null, "parser-1")
        );

        assertEquals(2, repository.items.size());
        assertEquals(List.of(1.0f, 0.0f, 0.0f), repository.items.get(0).embedding());
        assertEquals("test-embedding-model", repository.items.get(0).embeddingModel());
        assertEquals(3, repository.items.get(0).embeddingDimensions());
        assertEquals(64, repository.items.get(0).contentHash().length());
    }

    @Test
    void fetchesWorkspaceKnowledgeByTrimmedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.findWorkspaceKnowledge(" workspace-1 ");

        assertEquals("workspace-1", repository.workspaceId);
    }

    @Test
    void rejectsBlankWorkspaceId() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findWorkspaceKnowledge(" ")
        );

        assertEquals("Workspace ID must not be blank", exception.getMessage());
    }

    @Test
    void updatesWorkspaceKnowledgeField() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.updateWorkspaceKnowledgeField(" workspace-1 ", WorkspaceKnowledgeField.DOCUMENTS_INFO, " New info ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.DOCUMENT, repository.items.get(0).sourceType());
        assertEquals("New info", repository.items.get(0).content());
    }

    @Test
    void recordsDocumentsInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordDocumentsInfo("workspace-1", "document.txt", "job-1", "Document text");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.DOCUMENT, repository.items.get(0).sourceType());
        assertEquals("document.txt", repository.items.get(0).sourceName());
        assertEquals("job-1", repository.items.get(0).jobId());
        assertEquals("Document text", repository.items.get(0).content());
    }

    @Test
    void recordsDurableDocumentSourceMetadata() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());
        Instant extractedAt = Instant.parse("2026-09-07T12:00:00Z");

        service.recordDocumentsInfo(
                "workspace-1",
                "document.txt",
                "job-1",
                "Document text",
                new KnowledgeSourceMetadata(
                        "file-42",
                        "https://example.com/source",
                        extractedAt,
                        "parser-1"
                )
        );

        KnowledgeItem item = repository.items.get(0);
        assertEquals("file-42", item.sourceId());
        assertEquals("https://example.com/source", item.sourceUrl());
        assertEquals(extractedAt, item.extractedAt());
        assertEquals("parser-1", item.parserVersion());
    }

    @Test
    void recordsDocumentChunksWithStableSourceAndLocationMetadata() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordDocumentsInfo(
                "workspace-1",
                "report.pdf",
                "job-1",
                List.of(
                        new KnowledgeChunk(1, "Revenue increased", "Revenue", 2, null, null),
                        new KnowledgeChunk(2, "Costs decreased", "Costs", 3, null, null)
                ),
                new KnowledgeSourceMetadata("file-42", null, Instant.parse("2026-09-07T12:00:00Z"), "parser-1")
        );

        assertEquals(2, repository.items.size());
        assertEquals("file-42:1", repository.items.get(0).chunkId());
        assertEquals(1, repository.items.get(0).chunkSequence());
        assertEquals("Revenue", repository.items.get(0).heading());
        assertEquals(2, repository.items.get(0).pageNumber());
        assertEquals("file-42:2", repository.items.get(1).chunkId());
    }

    @Test
    void replacesPreviousChunksFromTheSameDocumentSource() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());
        KnowledgeSourceMetadata source = new KnowledgeSourceMetadata("file-42", null, null, "parser-1");

        service.recordDocumentsInfo(
                "workspace-1",
                "report.pdf",
                "job-1",
                List.of(
                        new KnowledgeChunk(1, "Old first", null, null, null, null),
                        new KnowledgeChunk(2, "Old second", null, null, null, null)
                ),
                source
        );
        service.recordDocumentsInfo(
                "workspace-1",
                "report.pdf",
                "job-2",
                List.of(new KnowledgeChunk(1, "New only", null, null, null, null)),
                source
        );

        assertEquals(1, repository.items.size());
        assertEquals("New only", repository.items.getFirst().content());
        assertEquals("file-42:1", repository.items.getFirst().id());
    }

    @Test
    void deletesKnowledgeByWorkspaceAndSource() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());
        service.recordDocumentsInfo(
                "workspace-1",
                "report.pdf",
                "job-1",
                List.of(new KnowledgeChunk(1, "Content", null, null, null, null)),
                new KnowledgeSourceMetadata("file-42", null, null, "parser-1")
        );

        service.deleteSourceKnowledge("workspace-1", "file-42");

        assertEquals(0, repository.items.size());
    }

    @Test
    void recordsAudioInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordAudioInfo("workspace-1", "meeting.mp3", "job-1", "Audio transcript");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.AUDIO, repository.items.get(0).sourceType());
        assertEquals("Audio transcript", repository.items.get(0).content());
    }

    @Test
    void recordsVideoInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordVideoInfo("workspace-1", "video.mp4", "job-1", "Video description");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.VIDEO, repository.items.get(0).sourceType());
        assertEquals("Video description", repository.items.get(0).content());
    }

    @Test
    void recordsImagesInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordImagesInfo("workspace-1", "image.png", "job-1", "Image description");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.IMAGE, repository.items.get(0).sourceType());
        assertEquals("Image description", repository.items.get(0).content());
    }

    @Test
    void rejectsMissingKnowledgeField() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", null, "Info")
        );

        assertEquals("Knowledge field must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankKnowledgeFieldValue() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", WorkspaceKnowledgeField.AUDIO_INFO, " ")
        );

        assertEquals("Knowledge field value must not be blank", exception.getMessage());
    }

    @Test
    void answersWorkspaceQuestionWithWorkspaceContext() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.knowledge = Optional.of(new WorkspaceKnowledge(
                "workspace-1",
                "Document context",
                "Audio context",
                "Video context",
                "Image context"
        ));
        repository.searchResults = List.of(
                KnowledgeItem.builder()
                        .id("item-1")
                        .workspaceId("workspace-1")
                        .sourceType(KnowledgeSourceType.DOCUMENT)
                        .sourceName("document.txt")
                        .jobId("job-1")
                        .sourceId("file-42")
                        .sourceUrl("https://example.com/source")
                        .content("Document context")
                        .extractedAt(Instant.parse("2026-07-01T23:59:00Z"))
                        .parserVersion("parser-1")
                        .chunkId("file-42:1")
                        .chunkSequence(1)
                        .heading("Overview")
                        .pageNumber(2)
                        .createdAt(Instant.parse("2026-07-02T00:00:00Z"))
                        .build(),
                new KnowledgeItem(
                        "item-2",
                        "workspace-1",
                        KnowledgeSourceType.AUDIO,
                        "meeting.mp3",
                        "job-1",
                        "Audio context",
                        Instant.parse("2026-07-02T00:00:01Z")
                )
        );
        CapturingKnowledgeAnswerProvider answerProvider = new CapturingKnowledgeAnswerProvider();
        KnowledgeService service = new KnowledgeService(repository, answerProvider);

        var answer = service.answerWorkspaceQuestion(" workspace-1 ", " What do we know? ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals("What do we know?", answer.question());
        assertEquals("Answer from LLM", answer.answer());
        assertEquals(2, answer.sourceFiles().size());
        assertEquals("document.txt", answer.sourceFiles().get(0).name());
        assertEquals("documents", answer.sourceFiles().get(0).type());
        assertEquals("job-1", answer.sourceFiles().get(0).jobId());
        assertEquals("file-42", answer.sourceFiles().get(0).sourceId());
        assertEquals("https://example.com/source", answer.sourceFiles().get(0).sourceUrl());
        assertEquals("parser-1", answer.sourceFiles().get(0).parserVersion());
        assertEquals(1, answer.sourceFiles().get(0).sourceCount());
        assertEquals(2, answer.sources().size());
        assertEquals("document.txt", answer.sources().get(0).sourceName());
        assertEquals("documents:file-42", answer.sources().get(0).sourceFileKey());
        assertEquals("file-42", answer.sources().get(0).sourceId());
        assertEquals("file-42:1", answer.sources().get(0).chunkId());
        assertEquals(2, answer.sources().get(0).pageNumber());
        assertEquals("What do we know?", answerProvider.question);
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Document context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Audio context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Source file: document.txt"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Location: page 2"));
    }

    @Test
    void usesHybridRetrievalWhenEmbeddingsAreConfigured() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.searchResults = List.of(KnowledgeItem.builder()
                .id("item-1")
                .workspaceId("workspace-1")
                .sourceType(KnowledgeSourceType.DOCUMENT)
                .content("Semantic result")
                .createdAt(Instant.now())
                .build());
        KnowledgeService service = embeddingService(repository);

        service.answerWorkspaceQuestion("workspace-1", "What grew?");

        assertEquals(List.of(0.0f, 1.0f, 0.0f), repository.queryEmbedding);
        assertEquals(32, repository.candidateLimit);
        assertEquals(60, repository.rrfRankConstant);
    }

    @Test
    void usesVectorOnlyRetrievalWhenConfigured() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.searchResults = List.of(KnowledgeItem.builder()
                .id("item-1")
                .workspaceId("workspace-1")
                .sourceType(KnowledgeSourceType.DOCUMENT)
                .content("Vector result")
                .createdAt(Instant.now())
                .build());
        KnowledgeService service = new KnowledgeService(
                repository,
                new CapturingKnowledgeAnswerProvider(),
                null,
                new KnowledgeValidator(),
                new FixedTextEmbeddingProvider(),
                new KnowledgeEmbeddingProperties(true, "test-embedding-model", 3, 32, 32, 60),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.VECTOR)
        );

        service.answerWorkspaceQuestion("workspace-1", "What grew?");

        assertTrue(repository.vectorOnly);
        assertEquals(32, repository.vectorCandidateLimit);
    }

    @Test
    void retrievesOneHundredCandidatesAndReranksToTwelve() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.searchResults = List.of(
                item("first", "First candidate"),
                item("second", "Second candidate")
        );
        CapturingTextReranker reranker = new CapturingTextReranker();
        KnowledgeService service = new KnowledgeService(
                repository,
                new CapturingKnowledgeAnswerProvider(),
                null,
                new KnowledgeValidator(),
                new FixedTextEmbeddingProvider(),
                new KnowledgeEmbeddingProperties(true, "test-embedding-model", 3, 32, 32, 60),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.VECTOR),
                reranker,
                rerankingProperties(),
                SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled()
        );

        var answer = service.answerWorkspaceQuestion("workspace-1", "Which candidate is relevant?");

        assertEquals(100, repository.vectorResultLimit);
        assertEquals(100, repository.vectorCandidateLimit);
        assertEquals("Which candidate is relevant?", reranker.query);
        assertEquals(12, reranker.limit);
        assertEquals(List.of("second", "first"), answer.sources().stream().map(source -> source.id()).toList());
    }

    @Test
    void interleavesOriginalAndExpandedHybridQueries() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.searchResults = List.of(item("original", "Original candidate"));
        repository.expandedSearchResults = List.of(item("expanded", "Expanded lexical candidate"));
        SearchQueryProvider queryProvider = new SearchQueryProvider() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public List<String> expand(String question, int limit) {
                return List.of("expanded query");
            }
        };
        KnowledgeService service = new KnowledgeService(
                repository,
                new CapturingKnowledgeAnswerProvider(),
                null,
                new KnowledgeValidator(),
                new FixedTextEmbeddingProvider(),
                new KnowledgeEmbeddingProperties(true, "test-embedding-model", 3, 32, 32, 60),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID),
                TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(),
                queryProvider,
                new KnowledgeQueryExpansionProperties(true, 2)
        );

        var answer = service.answerWorkspaceQuestion("workspace-1", "Original question");

        assertEquals(List.of("Original question", "expanded query"), repository.lexicalQueries);
        assertEquals(2, repository.vectorCalls);
        assertEquals(List.of("original", "expanded"), answer.sources().stream().map(source -> source.id()).toList());
    }

    private KnowledgeItem item(String id, String content) {
        return KnowledgeItem.builder()
                .id(id)
                .workspaceId("workspace-1")
                .sourceType(KnowledgeSourceType.DOCUMENT)
                .content(content)
                .createdAt(Instant.now())
                .build();
    }

    private KnowledgeRerankingProperties rerankingProperties() {
        return new KnowledgeRerankingProperties(
                true,
                "test-reranker",
                "file:/model.onnx",
                "file:/tokenizer.json",
                "cache",
                100,
                12,
                16,
                512
        );
    }

    private KnowledgeService embeddingService(CapturingKnowledgeRepository repository) {
        return new KnowledgeService(
                repository,
                new CapturingKnowledgeAnswerProvider(),
                null,
                new KnowledgeValidator(),
                new FixedTextEmbeddingProvider(),
                new KnowledgeEmbeddingProperties(true, "test-embedding-model", 3, 32, 32, 60)
        );
    }

    @Test
    void rejectsBlankQuestion() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", " ")
        );

        assertEquals("Question must not be blank", exception.getMessage());
    }

    @Test
    void rejectsTooLongQuestion() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", "a".repeat(4_001))
        );

        assertEquals("Question must not be longer than 4000 characters", exception.getMessage());
    }

    @Test
    void failsWhenWorkspaceKnowledgeIsMissing() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", "What do we know?")
        );

        assertEquals("Workspace knowledge was not found", exception.getMessage());
    }

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private String workspaceId;
        private final List<KnowledgeItem> items = new ArrayList<>();
        private List<KnowledgeItem> searchResults = List.of();
        private List<KnowledgeItem> expandedSearchResults = List.of();
        private final List<String> lexicalQueries = new ArrayList<>();
        private int vectorCalls;
        private Optional<WorkspaceKnowledge> knowledge = Optional.empty();
        private List<Float> queryEmbedding;
        private int candidateLimit;
        private int rrfRankConstant;
        private boolean vectorOnly;
        private int vectorResultLimit;
        private int vectorCandidateLimit;

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return knowledge;
        }

        @Override
        public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return items;
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) {
            this.workspaceId = workspaceId;
            lexicalQueries.add(query);
            if (query.equals("expanded query")) {
                return expandedSearchResults;
            }
            return searchResults;
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItems(
                String workspaceId,
                String query,
                List<Float> queryEmbedding,
                int limit,
                int candidateLimit,
                int rrfRankConstant
        ) {
            this.workspaceId = workspaceId;
            this.queryEmbedding = queryEmbedding;
            this.candidateLimit = candidateLimit;
            this.rrfRankConstant = rrfRankConstant;
            return searchResults;
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItemsByVector(
                String workspaceId, List<Float> queryEmbedding, int limit, int candidateLimit
        ) {
            this.workspaceId = workspaceId;
            this.queryEmbedding = queryEmbedding;
            this.vectorOnly = true;
            this.vectorCalls++;
            this.vectorResultLimit = limit;
            this.vectorCandidateLimit = candidateLimit;
            return searchResults;
        }

        @Override
        public void addKnowledgeItem(KnowledgeItem item) {
            this.workspaceId = item.workspaceId();
            items.add(item);
        }

        @Override
        public void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId) && sourceId.equals(item.sourceId()));
        }

        @Override
        public void deleteKnowledgeItemsByWorkspaceId(String workspaceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId));
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value) {
            addKnowledgeItem(new KnowledgeItem(
                    "item-" + items.size(),
                    workspaceId,
                    switch (field) {
                        case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
                        case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
                        case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
                        case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
                    },
                    field.fieldName(),
                    null,
                    value,
                    Instant.now()
            ));
        }
    }

    private static class FixedTextEmbeddingProvider implements TextEmbeddingProvider {

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String model() {
            return "test-embedding-model";
        }

        @Override
        public int dimensions() {
            return 3;
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts) {
            return texts.stream().map(ignored -> List.of(1.0f, 0.0f, 0.0f)).toList();
        }

        @Override
        public List<Float> embedQuery(String text) {
            return List.of(0.0f, 1.0f, 0.0f);
        }
    }

    private static class CapturingTextReranker implements TextReranker {

        private String query;
        private int limit;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<KnowledgeItem> rerank(String query, List<KnowledgeItem> candidates, int limit) {
            this.query = query;
            this.limit = limit;
            List<KnowledgeItem> reranked = new ArrayList<>(candidates);
            java.util.Collections.reverse(reranked);
            return List.copyOf(reranked);
        }
    }

    private static class CapturingKnowledgeAnswerProvider implements KnowledgeAnswerProvider {

        private String question;
        private String context;

        @Override
        public String answer(String question, String context) {
            this.question = question;
            this.context = context;
            return "Answer from LLM";
        }
    }
}
