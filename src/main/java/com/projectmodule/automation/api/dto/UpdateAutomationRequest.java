package com.projectmodule.automation.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/automations/{id}}. A field left {@code null} is treated
 * as "not supplied" and left unchanged — the same convention every other {@code UpdateXRequest}
 * in this module already uses. {@code version} is required for the optimistic check.
 */
public record UpdateAutomationRequest(
        String name,
        String description,
        String actionMessage,
        Boolean enabled,

        @NotNull(message = "must not be null")
        Long version) {
}
