package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.config.KnowledgeEmbeddingProperties;
import com.aiworkspace.knowledge.config.KnowledgeQueryExpansionProperties;
import com.aiworkspace.knowledge.config.KnowledgeRerankingProperties;
import com.aiworkspace.knowledge.config.KnowledgeSearchProperties;
import com.aiworkspace.knowledge.interfaces.SearchQueryProvider;
import com.aiworkspace.knowledge.interfaces.TextEmbeddingProvider;
import com.aiworkspace.knowledge.interfaces.TextReranker;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.observability.SearchTelemetry;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetrievalService {

    private static final int RETRIEVAL_LIMIT = 12;
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final TextEmbeddingProvider textEmbeddingProvider;
    private final KnowledgeEmbeddingProperties embeddingProperties;
    private final KnowledgeQueryExpansionProperties queryExpansionProperties;
    private final KnowledgeRerankingProperties rerankingProperties;
    private final SearchQueryProvider searchQueryProvider;
    private final KnowledgeSearchProperties searchProperties;
    private final TextReranker textReranker;
    private final SearchTelemetry telemetry;

    public KnowledgeRetrievalService(KnowledgeRepository knowledgeRepository,
            TextEmbeddingProvider textEmbeddingProvider, KnowledgeEmbeddingProperties embeddingProperties,
            KnowledgeSearchProperties searchProperties, TextReranker textReranker,
            KnowledgeRerankingProperties rerankingProperties, SearchQueryProvider searchQueryProvider,
            KnowledgeQueryExpansionProperties queryExpansionProperties, SearchTelemetry telemetry) {
        this.knowledgeRepository = knowledgeRepository;
        this.textEmbeddingProvider = textEmbeddingProvider;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.textReranker = textReranker;
        this.rerankingProperties = rerankingProperties;
        this.searchQueryProvider = searchQueryProvider;
        this.queryExpansionProperties = queryExpansionProperties;
        this.telemetry = telemetry == null ? SearchTelemetry.NOOP : telemetry;
    }

    public List<KnowledgeItem> retrieveWithNeighbors(String workspaceId, String question) throws IOException {
        return knowledgeRepository.expandNeighbors(workspaceId, retrieve(workspaceId, question));
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

        int candidateLimit = retrievalCandidateLimit();
        List<KnowledgeItem> candidates;
        String stage = "embedding";
        try {
            List<Float> queryEmbedding = textEmbeddingProvider.embedQuery(question);
            if (searchProperties.vectorOnly()) {
                stage = "vector_search";
                candidates = knowledgeRepository.searchKnowledgeItemsByVector(
                        workspaceId, queryEmbedding, candidateLimit, candidateLimit);
            } else {
                stage = "hybrid_search";
                candidates = knowledgeRepository.searchKnowledgeItems(
                        workspaceId, question, queryEmbedding, candidateLimit, candidateLimit,
                        embeddingProperties.rrfRankConstant());
            }
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback(stage);
            LOGGER.warn("Retrieval fallback stage={} workspaceId={} exception={}",
                    stage, workspaceId, exception.getClass().getSimpleName());
            return knowledgeRepository.searchKnowledgeItems(workspaceId, question, RETRIEVAL_LIMIT);
        }

        return rerank(question, candidates);
    }

    private List<String> searchQueries(String question) {
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (searchQueryProvider == null || !searchQueryProvider.isConfigured() || !queryExpansionProperties.enabled()) {
            return List.copyOf(queries);
        }
        try {
            for (String expanded : searchQueryProvider.expand(question, queryExpansionProperties.queryLimit())) {
                if (expanded != null && !expanded.isBlank()
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
        int perQueryLimit = retrievalCandidateLimit();
        int mergedLimit = rerankingEnabled() ? rerankingProperties.candidateLimit() : RETRIEVAL_LIMIT;
        List<List<KnowledgeItem>> rankings = new ArrayList<>();
        try {
            for (String query : queries) {
                List<Float> embedding = textEmbeddingProvider.embedQuery(query);
                rankings.add(knowledgeRepository.searchKnowledgeItemsByVector(
                        workspaceId, embedding, perQueryLimit, perQueryLimit));
                if (!searchProperties.vectorOnly()) {
                    rankings.add(knowledgeRepository.searchKnowledgeItems(workspaceId, query, perQueryLimit));
                }
            }
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback("expanded_search");
            LOGGER.warn("Expanded retrieval fallback workspaceId={} exception={}",
                    workspaceId, exception.getClass().getSimpleName());
            return knowledgeRepository.searchKnowledgeItems(workspaceId, question, RETRIEVAL_LIMIT);
        }
        return rerank(question, ReciprocalRankFusion.fuse(
                rankings, mergedLimit, embeddingProperties.rrfRankConstant()));
    }

    private int retrievalCandidateLimit() {
        int limit = Math.max(RETRIEVAL_LIMIT, embeddingProperties.candidateLimit());
        return rerankingEnabled() ? Math.max(limit, rerankingProperties.candidateLimit()) : limit;
    }

    private List<KnowledgeItem> rerank(String question, List<KnowledgeItem> candidates) {
        if (!rerankingEnabled()) {
            return candidates.stream().limit(RETRIEVAL_LIMIT).toList();
        }
        try {
            return telemetry.measure("search.rerank", () -> textReranker.rerank(
                    question, candidates.stream().limit(rerankingProperties.candidateLimit()).toList(),
                    rerankingProperties.resultLimit()));
        } catch (IOException | RuntimeException exception) {
            telemetry.fallback("reranking");
            LOGGER.warn("Reranking fallback model={} exception={}",
                    rerankingProperties.model(), exception.getClass().getSimpleName());
            return candidates.stream().limit(RETRIEVAL_LIMIT).toList();
        }
    }

    private boolean rerankingEnabled() {
        return textReranker != null && textReranker.isConfigured() && rerankingProperties.enabled();
    }
}
