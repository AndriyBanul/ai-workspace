package com.aiworkspace.knowledge.interfaces;

import java.util.List;

@FunctionalInterface
public interface EmbeddingTextSplitter {
    List<String> split(String prefix, String text);
}
