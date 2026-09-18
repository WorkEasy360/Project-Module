package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/milestones}.
 *
 * <p>{@code phaseId} is optional: a milestone may belong directly to the project with no phase
 * grouping. A milestone is always created {@code PENDING}; status is not settable at creation.
 */
public record CreateMilestoneRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate dueDate,

        UUID phaseId) {
}
