package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.config.KnowledgeQueryExpansionProperties;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.config.KnowledgeSearchProperties;
import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.interfaces.SearchQueryProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.workspaces.services.WorkspaceService;

public final class KnowledgeServiceTestFactory {

    private KnowledgeServiceTestFactory() {
    }

    public static KnowledgeService create(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider) {
        return create(knowledgeRepository, knowledgeAnswerProvider, null, new KnowledgeValidator(), null,
                new KnowledgeEmbeddingProperties(false, null, null, null, null, null),
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID), TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public static KnowledgeService create(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties) {
        return create(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties,
                new KnowledgeSearchProperties(KnowledgeSearchProperties.HYBRID), TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public static KnowledgeService create(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties) {
        return create(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties, searchProperties, TextReranker.NONE,
                KnowledgeRerankingProperties.disabled(), SearchQueryProvider.NONE,
                KnowledgeQueryExpansionProperties.disabled());
    }

    public static KnowledgeService create(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties, TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties, SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties) {
        return create(knowledgeRepository, knowledgeAnswerProvider, workspaceService, knowledgeValidator,
                textEmbeddingProvider, embeddingProperties, searchProperties, textReranker, rerankingProperties,
                searchQueryProvider, queryExpansionProperties, SearchTelemetry.NOOP);
    }

    public static KnowledgeService create(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider,
            WorkspaceService workspaceService, KnowledgeValidator knowledgeValidator,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties, TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties, SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties, SearchTelemetry telemetry) {
        KnowledgeCitationService citationService = new KnowledgeCitationService();
        return new KnowledgeService(knowledgeRepository, workspaceService, knowledgeValidator,
                new KnowledgeIndexingService(knowledgeRepository, knowledgeValidator, textEmbeddingProvider, null),
                new KnowledgeRetrievalService(knowledgeRepository, textEmbeddingProvider, embeddingProperties,
                        searchProperties, textReranker, rerankingProperties, searchQueryProvider,
                        queryExpansionProperties, telemetry),
                new KnowledgeAnswerService(knowledgeAnswerProvider, citationService), null);
    }
}
