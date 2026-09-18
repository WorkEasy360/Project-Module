package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/dependencies/{id}}.
 *
 * <p>{@code broken} is the only editable field — a dependency has no name or description, and
 * which two tasks it connects is immutable once created. {@code broken} may only move to
 * {@code true}: there is no {@code dependency.unbroken} event, so setting it back to
 * {@code false} is rejected. {@code version} is required for the optimistic check.
 */
public record UpdateDependencyRequest(

        Boolean broken,

        @NotNull(message = "must not be null")
        Long version) {
}
