package com.aiworkspace.models;

import java.util.List;

public record RetrievalEvaluationCorpus(
        int version,
        List<RetrievalEvaluationSource> documents,
        List<RetrievalEvaluationQuestion> questions
) {
}
