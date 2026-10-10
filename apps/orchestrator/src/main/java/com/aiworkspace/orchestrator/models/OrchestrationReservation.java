package com.aiworkspace.orchestrator.models;

import com.aiworkspace.orchestrator.entities.OrchestrationSubmissionRequestEntity;

public record OrchestrationReservation(OrchestrationSubmissionRequestEntity entity, OrchestrationSubmission replayed) {
}
