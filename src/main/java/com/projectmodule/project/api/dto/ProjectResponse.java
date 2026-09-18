package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full representation of a project, returned by create, get and update.
 *
 * <p>Built by {@link com.projectmodule.project.api.ProjectMapper} from the {@code Project}
 * aggregate. The entity itself is never serialized directly.
 */
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        ProjectStatus status,
        ProjectPriority priority,
        LocalDate startDate,
        LocalDate targetEndDate,
        UUID ownerId,
        boolean archived,
        Instant archivedAt,
        UUID archivedBy,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
