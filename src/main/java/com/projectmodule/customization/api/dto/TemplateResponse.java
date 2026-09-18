package com.projectmodule.customization.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import java.time.Instant;
import java.util.UUID;

/** Representation of a project template, returned by create, get, list and update. */
public record TemplateResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        ProjectPriority defaultPriority,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
