package com.projectmodule.project.api;

import com.projectmodule.project.api.dto.ProjectResponse;
import com.projectmodule.project.api.dto.ProjectSummaryResponse;
import com.projectmodule.project.domain.Project;
import org.springframework.stereotype.Component;

/**
 * Maps the {@link Project} aggregate to its API representations.
 *
 * <p>Plain, hand-written mapping. MapStruct is not introduced for this slice; a manual mapper
 * is simple enough at this field count and keeps the dependency list unchanged.
 */
@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOrganizationId().value(),
                project.getName(),
                project.getDescription().orElse(null),
                project.getStatus(),
                project.getPriority(),
                project.getStartDate().orElse(null),
                project.getTargetEndDate().orElse(null),
                project.getOwnerId().value(),
                project.isArchived(),
                project.getArchivedAt().orElse(null),
                project.getArchivedBy().map(id -> id.value()).orElse(null),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion(),
                project.getCustomerContact().orElse(null),
                project.getTags());
    }

    public ProjectSummaryResponse toSummary(Project project) {
        return toSummary(project, 0, 0);
    }

    /** With the project's task counts (live tasks; completed ones among them). */
    public ProjectSummaryResponse toSummary(Project project, long taskCount, long completedTaskCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getPriority(),
                project.getOwnerId().value(),
                project.getUpdatedAt(),
                project.getDescription().orElse(null),
                project.getStartDate().orElse(null),
                project.getTargetEndDate().orElse(null),
                project.getCustomerContact().orElse(null),
                project.getTags(),
                project.isArchived(),
                project.getArchivedAt().orElse(null),
                project.getCreatedAt(),
                taskCount,
                completedTaskCount);
    }
}
