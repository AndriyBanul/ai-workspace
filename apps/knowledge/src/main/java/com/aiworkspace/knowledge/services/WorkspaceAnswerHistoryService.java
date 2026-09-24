package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.entities.WorkspaceAnswerHistoryEntity;
import com.aiworkspace.knowledge.models.WorkspaceAnswerHistoryEntry;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import com.aiworkspace.knowledge.repositories.WorkspaceAnswerHistoryRepository;
import com.aiworkspace.workspaces.services.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceAnswerHistoryService {

    private final WorkspaceAnswerHistoryRepository history;
    private final WorkspaceService workspaces;
    private final KnowledgeService knowledge;
    private final ObjectMapper json;

    public WorkspaceAnswerHistoryService(WorkspaceAnswerHistoryRepository history, WorkspaceService workspaces,
            KnowledgeService knowledge, ObjectMapper json) {
        this.history = history;
        this.workspaces = workspaces;
        this.knowledge = knowledge;
        this.json = json;
    }

    public WorkspaceKnowledgeAnswer ask(String ownerId, String workspaceId, WorkspaceQuestionRequest question)
            throws IOException {
        WorkspaceKnowledgeAnswer answer = knowledge.answerWorkspaceQuestion(ownerId, workspaceId, question);
        save(answer);
        return answer;
    }

    @Transactional
    public void save(WorkspaceKnowledgeAnswer answer) throws IOException {
        history.save(new WorkspaceAnswerHistoryEntity(UUID.randomUUID().toString(), answer.workspaceId(),
                answer.question(), json.writeValueAsString(answer), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceAnswerHistoryEntry> list(String ownerId, String workspaceId, int limit) throws IOException {
        String ownedId = workspaces.getWorkspace(ownerId, workspaceId).id();
        int size = Math.max(1, Math.min(limit, 100));
        List<WorkspaceAnswerHistoryEntry> entries = new java.util.ArrayList<>();
        for (var item : history.findByWorkspaceIdOrderByCreatedAtDesc(ownedId, PageRequest.of(0, size))) {
            entries.add(new WorkspaceAnswerHistoryEntry(item.getId(), item.getCreatedAt(),
                    json.readValue(item.getAnswerJson(), WorkspaceKnowledgeAnswer.class)));
        }
        return List.copyOf(entries);
    }
}
