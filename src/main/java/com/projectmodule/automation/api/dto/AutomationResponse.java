package com.projectmodule.automation.api.dto;

import com.projectmodule.automation.domain.AutomationActionType;
import java.time.Instant;
import java.util.UUID;

/** Representation of an automation rule, returned by create, list and update. */
public record AutomationResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        String triggerEvent,
        AutomationActionType actionType,
        UUID actionRecipientId,
        String actionChannelReference,
        String actionMessage,
        boolean enabled,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
