package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/issues/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. There is no
 * {@code status} field: {@code docs/project/05-EVENTS.md} documents no Issue events at all, so
 * no status transition is modelled. {@code version} is required for the optimistic check.
 */
public record UpdateIssueRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        ProjectPriority priority,

        @NotNull(message = "must not be null")
        Long version) {
}
