package com.projectmodule.automation.api.dto;

import com.projectmodule.automation.domain.AutomationRunStatus;
import java.time.Instant;
import java.util.UUID;

/** Representation of one automation execution, returned by the run-history endpoint. */
public record AutomationRunResponse(
        UUID id,
        UUID automationId,
        UUID projectId,
        String triggerEvent,
        AutomationRunStatus status,
        String errorMessage,
        Instant executedAt) {
}
