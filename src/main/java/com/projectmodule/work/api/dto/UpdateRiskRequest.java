package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.RiskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/risks/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code status}
 * may only move to {@link RiskStatus#RESOLVED} — the one transition
 * {@code docs/project/05-EVENTS.md} has an event for. Setting it back to
 * {@link RiskStatus#OPEN} is rejected; there is no event for that. {@code version} is required
 * for the optimistic check.
 */
public record UpdateRiskRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        ProjectPriority priority,

        RiskStatus status,

        @NotNull(message = "must not be null")
        Long version) {
}
