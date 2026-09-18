package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.RiskStatus;
import java.time.Instant;
import java.util.UUID;

/** Representation of a risk, returned by create, list and update. */
public record RiskResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        ProjectPriority priority,
        RiskStatus status,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
