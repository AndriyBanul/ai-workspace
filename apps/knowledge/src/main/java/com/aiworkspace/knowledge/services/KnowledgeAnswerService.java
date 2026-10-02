package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAnswerService {

    private final KnowledgeAnswerProvider answerProvider;
    private final KnowledgeCitationService citationService;

    public KnowledgeAnswerService(KnowledgeAnswerProvider answerProvider, KnowledgeCitationService citationService) {
        this.answerProvider = answerProvider;
        this.citationService = citationService;
    }

    public WorkspaceKnowledgeAnswer answer(String workspaceId, String question, List<KnowledgeItem> evidence)
            throws IOException {
        String answer = answerProvider.answer(question, citationService.answerContext(workspaceId, evidence));
        return new WorkspaceKnowledgeAnswer(
                workspaceId,
                question,
                answer,
                citationService.sourceFiles(evidence),
                citationService.sources(evidence)
        );
    }
}
