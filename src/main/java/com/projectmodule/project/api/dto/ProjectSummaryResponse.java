package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight representation of a project, returned by the paginated list endpoint.
 *
 * <p>Deliberately narrower than {@link ProjectResponse}: a list view does not need the
 * description, dates or archive detail, and omitting them keeps a page of results small.
 */
public record ProjectSummaryResponse(
        UUID id,
        String name,
        ProjectStatus status,
        ProjectPriority priority,
        UUID ownerId,
        Instant updatedAt) {
}
