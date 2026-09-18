package com.projectmodule.dashboard.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.dashboard.api.dto.DashboardResponse;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the dashboard read model from existing project data.
 *
 * <p>Holds no business rule and owns no state: every number here is derived, on read, from the
 * {@code projects} table through {@link ProjectRepository}. There is nothing to keep in sync,
 * because nothing is stored.
 *
 * <p>Scoped to the caller's organization only, the same tenant boundary
 * {@code ProjectApplicationService.listProjects} and {@code createProject} already use. Not
 * gated through {@code ProjectAuthorizationService}: that interface decides permission on one
 * project, and this summary spans every project in the organization, so there is no single
 * {@code projectId} for it to check against.
 */
@Service
public class DashboardApplicationService {

    private final ProjectRepository projectRepository;

    public DashboardApplicationService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(RequestContext context) {
        OrganizationId organizationId = requireOrganizationId(context);

        long activeCount = projectRepository
                .countByOrganizationIdAndArchivedAtIsNull(organizationId.value());
        long archivedCount = projectRepository
                .countByOrganizationIdAndArchivedAtIsNotNull(organizationId.value());

        Map<ProjectStatus, Long> byStatus = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (ProjectRepository.StatusCount row : projectRepository.countActiveByStatus(organizationId.value())) {
            byStatus.put(row.getStatus(), row.getTotal());
        }

        Map<ProjectPriority, Long> byPriority = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority priority : ProjectPriority.values()) {
            byPriority.put(priority, 0L);
        }
        for (ProjectRepository.PriorityCount row : projectRepository.countActiveByPriority(organizationId.value())) {
            byPriority.put(row.getPriority(), row.getTotal());
        }

        return new DashboardResponse(activeCount, archivedCount, byStatus, byPriority);
    }

    private static OrganizationId requireOrganizationId(RequestContext context) {
        return context.organizationId().orElseThrow(() -> new MissingIdentityException(
                "No organization was supplied with this request"));
    }
}
