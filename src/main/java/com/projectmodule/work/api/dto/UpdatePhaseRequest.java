package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for {@code PATCH /api/v1/phases/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and is left unchanged, the same
 * convention {@code UpdateProjectRequest} uses. {@code version} is required for the optimistic
 * check.
 */
public record UpdatePhaseRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "must not be null")
        Long version) {
}
