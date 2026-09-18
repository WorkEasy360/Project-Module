package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/task-lists/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code version}
 * is required for the optimistic check.
 */
public record UpdateTaskListRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        UUID phaseId,

        @NotNull(message = "must not be null")
        Long version) {
}
