package com.aiworkspace.knowledge.providers;

import java.io.IOException;

public interface KnowledgeAnswerProvider {

    String answer(String question, String context) throws IOException;
}
