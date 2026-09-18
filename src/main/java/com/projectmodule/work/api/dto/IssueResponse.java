package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import java.time.Instant;
import java.util.UUID;

/** Representation of an issue, returned by create, list and update. */
public record IssueResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        ProjectPriority priority,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
