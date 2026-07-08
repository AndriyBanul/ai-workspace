package com.aiworkspace.knowledge.interfaces;

import java.io.IOException;

public interface KnowledgeAnswerProvider {

    String answer(String question, String context) throws IOException;
}
