package com.aiworkspace.models;

import java.util.List;

public record RetrievalEvaluationRun(
        RetrievalEvaluationConfig config,
        String mode,
        String split,
        double hit1,
        double hit3,
        double hit5,
        double contextHit,
        List<RetrievalEvaluationOutcome> outcomes
) {
}
