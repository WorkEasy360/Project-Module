package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/subtasks/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code version}
 * is required for the optimistic check.
 */
public record UpdateSubtaskRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        Boolean completed,

        @NotNull(message = "must not be null")
        Long version) {
}
