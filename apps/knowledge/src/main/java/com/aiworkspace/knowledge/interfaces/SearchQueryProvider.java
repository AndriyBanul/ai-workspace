package com.aiworkspace.knowledge.interfaces;

import java.io.IOException;
import java.util.List;

public interface SearchQueryProvider {

    SearchQueryProvider NONE = new SearchQueryProvider() {
        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public List<String> expand(String question, int limit) {
            return List.of();
        }
    };

    boolean isConfigured();

    List<String> expand(String question, int limit) throws IOException;
}
