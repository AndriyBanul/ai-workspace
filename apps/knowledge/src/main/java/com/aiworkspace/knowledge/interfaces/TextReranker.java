package com.aiworkspace.knowledge.interfaces;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import java.io.IOException;
import java.util.List;

public interface TextReranker {

    TextReranker NONE = new TextReranker() {
        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public List<KnowledgeItem> rerank(String query, List<KnowledgeItem> candidates, int limit) {
            return candidates.stream().limit(limit).toList();
        }
    };

    boolean isConfigured();

    List<KnowledgeItem> rerank(String query, List<KnowledgeItem> candidates, int limit) throws IOException;
}
