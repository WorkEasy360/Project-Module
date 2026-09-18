package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/decisions/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. There is no
 * {@code status} field: the approved specification (`09-DECISION-SPEC.md`) defines no
 * status/lifecycle for Decision. {@code version} is required for the optimistic check.
 */
public record UpdateDecisionRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        UUID decidedBy,

        @NotNull(message = "must not be null")
        Long version) {
}
