package com.aiworkspace.knowledge.interfaces;

import java.io.IOException;
import java.util.List;

public interface TextEmbeddingProvider {

    boolean isConfigured();

    String model();

    int dimensions();

    List<List<Float>> embedDocuments(List<String> texts) throws IOException;

    List<Float> embedQuery(String text) throws IOException;
}
