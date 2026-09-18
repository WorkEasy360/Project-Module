package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.MilestoneStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/milestones/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. This is the
 * only documented mutation endpoint for a milestone besides delete, so status transitions go
 * through it too: {@code status} may only move to {@link MilestoneStatus#AT_RISK} or
 * {@link MilestoneStatus#COMPLETED} — the two transitions {@code docs/project/05-EVENTS.md} has
 * an event for. Setting it back to {@link MilestoneStatus#PENDING} is rejected; there is no
 * event for that transition. {@code version} is required for the optimistic check.
 */
public record UpdateMilestoneRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate dueDate,

        UUID phaseId,

        MilestoneStatus status,

        @NotNull(message = "must not be null")
        Long version) {
}
