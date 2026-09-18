package com.projectmodule.dashboard.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.util.Map;

/**
 * Organization-wide project summary: the P0 "basic dashboard".
 *
 * <p>{@code byStatus} and {@code byPriority} always carry every enum value, including ones with
 * a zero count, so a consumer never has to treat a missing key as meaning zero.
 *
 * <p>Scoped to active (non-archived) projects except {@code archivedProjectCount} itself,
 * matching the existing project-list convention of hiding archived projects by default.
 */
public record DashboardResponse(
        long activeProjectCount,
        long archivedProjectCount,
        Map<ProjectStatus, Long> byStatus,
        Map<ProjectPriority, Long> byPriority) {
}
