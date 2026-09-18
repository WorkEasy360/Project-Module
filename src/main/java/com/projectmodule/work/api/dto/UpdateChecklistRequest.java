package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/checklists/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code version}
 * is required for the optimistic check.
 */
public record UpdateChecklistRequest(

        @Size(max = 500, message = "must be at most 500 characters")
        String text,

        Boolean checked,

        UUID subtaskId,

        @NotNull(message = "must not be null")
        Long version) {
}
