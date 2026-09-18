package com.projectmodule.automation.api.dto;

import com.projectmodule.automation.domain.AutomationActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{projectId}/automations}.
 *
 * <p>{@code actionRecipientId}/{@code actionChannelReference} requiredness is conditional on
 * {@code actionType}, enforced by {@code ProjectAutomation}'s own domain validation, not Bean
 * Validation here — the same reasoning {@code RequestRecommendationRequest} already uses.
 */
public record CreateAutomationRequest(

        @NotBlank(message = "must not be blank")
        String name,

        String description,

        @NotBlank(message = "must not be blank")
        String triggerEvent,

        @NotNull(message = "must not be null")
        AutomationActionType actionType,

        UUID actionRecipientId,

        String actionChannelReference,

        @NotBlank(message = "must not be blank")
        String actionMessage) {
}
