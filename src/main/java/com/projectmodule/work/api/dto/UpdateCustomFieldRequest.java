package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/custom-fields/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. There is no
 * {@code valueType} field: per the approved specification (§9/Decision #5), a custom field's
 * value type is immutable after creation. {@code version} is required for the optimistic check.
 */
public record UpdateCustomFieldRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String value,

        @NotNull(message = "must not be null")
        Long version) {
}
