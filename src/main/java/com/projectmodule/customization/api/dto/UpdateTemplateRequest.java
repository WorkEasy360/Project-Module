package com.projectmodule.customization.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/templates/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code version}
 * is required for the optimistic check.
 */
public record UpdateTemplateRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        ProjectPriority defaultPriority,

        @NotNull(message = "must not be null")
        Long version) {
}
