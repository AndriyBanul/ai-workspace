package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.config.KnowledgeQueryExpansionProperties;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.config.KnowledgeSearchProperties;
import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.interfaces.SearchQueryProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.models.KnowledgeChunk;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Stable facade for knowledge workflows used by API and media modules. */
@Service
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final WorkspaceService workspaceService;
    private final KnowledgeValidator knowledgeValidator;
    private final KnowledgeIndexingService indexingService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeAnswerService answerService;
    private final SourceIndexManifestService manifestService;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider) {
        this(knowledgeRepository, knowledgeAnswerProvider, null, new KnowledgeValidator(), null,
                new KnowledgeEmbeddingProperties(false, null, null, null, null, null),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID), TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties) {
        this(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties,
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID), TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties) {
        this(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties, searchProperties, TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties, TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties, SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties) {
        this(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties, searchProperties, textReranker, rerankingProperties,
                searchQueryProvider, queryExpansionProperties, SearchTelemetry.NOOP);
    }

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties, TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties, SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties, SearchTelemetry telemetry) {
        KnowledgeCitationService citationService = new KnowledgeCitationService();
        this.knowledgeRepository = knowledgeRepository;
        this.workspaceService = workspaceService;
        this.knowledgeValidator = knowledgeValidator;
        this.indexingService = new KnowledgeIndexingService(
                knowledgeRepository, knowledgeValidator, textEmbeddingProvider);
        this.retrievalService = new KnowledgeRetrievalService(
                knowledgeRepository, textEmbeddingProvider, embeddingProperties, searchProperties, textReranker,
                rerankingProperties, searchQueryProvider, queryExpansionProperties, telemetry);
        this.answerService = new KnowledgeAnswerService(knowledgeAnswerProvider, citationService);
        this.manifestService = null;
    }

    @Autowired
    public KnowledgeService(KnowledgeRepository knowledgeRepository, WorkspaceService workspaceService,
            KnowledgeValidator knowledgeValidator, KnowledgeIndexingService indexingService,
            KnowledgeRetrievalService retrievalService, KnowledgeAnswerService answerService,
            SourceIndexManifestService manifestService) {
        this.knowledgeRepository = knowledgeRepository;
        this.workspaceService = workspaceService;
        this.knowledgeValidator = knowledgeValidator;
        this.indexingService = indexingService;
        this.retrievalService = retrievalService;
        this.answerService = answerService;
        this.manifestService = manifestService;
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
        indexingService.record(workspaceId, sourceTypeFrom(field), field.fieldName(), null, value, null);
    }

    public void recordDocumentsInfo(String workspaceId, String sourceName, String jobId, String value)
            throws IOException {
        recordDocumentsInfo(workspaceId, sourceName, jobId, value, null);
    }

    public StagedKnowledgeIndex recordDocumentsInfo(String workspaceId, String sourceName, String jobId, String value,
            KnowledgeSourceMetadata sourceMetadata) throws IOException {
        return indexingService.record(
                workspaceId, KnowledgeSourceType.DOCUMENT, sourceName, jobId, value, sourceMetadata);
    }

    public StagedKnowledgeIndex recordDocumentsInfo(String workspaceId, String sourceName, String jobId,
            List<KnowledgeChunk> chunks, KnowledgeSourceMetadata sourceMetadata) throws IOException {
        return indexingService.recordChunks(
                workspaceId, KnowledgeSourceType.DOCUMENT, sourceName, jobId, chunks, sourceMetadata, "Document");
    }

    public void recordAudioInfo(String workspaceId, String sourceName, String jobId, String value)
            throws IOException {
        indexingService.record(workspaceId, KnowledgeSourceType.AUDIO, sourceName, jobId, value, null);
    }

    public StagedKnowledgeIndex recordAudioInfo(String workspaceId, String sourceName, String jobId,
            List<KnowledgeChunk> chunks,
            KnowledgeSourceMetadata sourceMetadata) throws IOException {
        return indexingService.recordChunks(
                workspaceId, KnowledgeSourceType.AUDIO, sourceName, jobId, chunks, sourceMetadata, "Audio");
    }

    public void recordVideoInfo(String workspaceId, String sourceName, String jobId, String value)
            throws IOException {
        indexingService.record(workspaceId, KnowledgeSourceType.VIDEO, sourceName, jobId, value, null);
    }

    public StagedKnowledgeIndex recordVideoInfo(String workspaceId, String sourceName, String jobId, String value,
            KnowledgeSourceMetadata sourceMetadata) throws IOException {
        return indexingService.record(workspaceId, KnowledgeSourceType.VIDEO, sourceName, jobId, value, sourceMetadata);
    }

    public void recordImagesInfo(String workspaceId, String sourceName, String jobId, String value)
            throws IOException {
        indexingService.record(workspaceId, KnowledgeSourceType.IMAGE, sourceName, jobId, value, null);
    }

    public StagedKnowledgeIndex recordImagesInfo(String workspaceId, String sourceName, String jobId, String value,
            KnowledgeSourceMetadata sourceMetadata) throws IOException {
        return indexingService.record(workspaceId, KnowledgeSourceType.IMAGE, sourceName, jobId, value, sourceMetadata);
    }

    public void deleteSourceKnowledge(String workspaceId, String sourceId) throws IOException {
        indexingService.deleteSource(workspaceId, sourceId);
    }

    public void deleteWorkspaceKnowledge(String workspaceId) throws IOException {
        indexingService.deleteWorkspace(workspaceId);
    }

    public boolean hasSourceKnowledge(String workspaceId, String sourceId) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Source ID must not be blank");
        }
        if (manifestService != null) {
            var manifest = manifestService.findBySourceId(sourceId.trim());
            if (manifest.isPresent()) {
                var active = manifest.get();
                return workspaceId.trim().equals(active.getWorkspaceId())
                        && knowledgeRepository.countSourceGeneration(workspaceId.trim(), sourceId.trim(),
                                active.getActiveGeneration()) == active.getExpectedItems();
            }
        }
        return knowledgeRepository.findKnowledgeItemsByWorkspaceId(workspaceId.trim()).stream()
                .anyMatch(item -> sourceId.trim().equals(item.sourceId()));
    }

    public void pruneInactiveSourceGenerations(String workspaceId, String sourceId) throws IOException {
        if (manifestService == null) return;
        var manifest = manifestService.findBySourceId(sourceId);
        if (manifest.isPresent() && workspaceId.equals(manifest.get().getWorkspaceId())) {
            knowledgeRepository.pruneSourceGenerations(workspaceId, sourceId, manifest.get().getActiveGeneration());
        }
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(String workspaceId, String question) throws IOException {
        knowledgeValidator.validateWorkspaceId(workspaceId);
        knowledgeValidator.validateQuestion(question);
        String trimmedWorkspaceId = workspaceId.trim();
        String trimmedQuestion = question.trim();
        List<KnowledgeItem> items = retrievalService.retrieveWithNeighbors(trimmedWorkspaceId, trimmedQuestion);
        if (items.isEmpty()) {
            throw new NoSuchElementException("Workspace knowledge was not found");
        }
        return answerService.answer(trimmedWorkspaceId, trimmedQuestion, items);
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(String ownerId, String workspaceId,
            WorkspaceQuestionRequest request) throws IOException {
        knowledgeValidator.validateQuestionRequest(request);
        workspaceService.getWorkspace(ownerId, workspaceId);
        return answerWorkspaceQuestion(workspaceId, request.question());
    }

    private KnowledgeSourceType sourceTypeFrom(WorkspaceKnowledgeField field) {
        return switch (field) {
            case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
            case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
            case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
            case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
        };
    }
}
