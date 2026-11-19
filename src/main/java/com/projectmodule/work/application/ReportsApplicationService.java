package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's summary report: how many of its active tasks are in each status. Every status is
 * present, including zero counts. Scoped to one project, so it uses the same {@code VIEW_PROJECT}
 * check as every other project-scoped read.
 */
@Service
public class ReportsApplicationService {

    private final TaskRepository taskRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ReportsApplicationService(TaskRepository taskRepository,
                                     ProjectApplicationService projectApplicationService,
                                     ProjectAuthorizationService projectAuthorizationService) {
        this.taskRepository = taskRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public ProjectSummaryReportResponse getSummary(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        Map<TaskStatus, Long> taskCountByStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            taskCountByStatus.put(status, 0L);
        }
        for (TaskRepository.StatusCount row : taskRepository.countActiveByStatus(projectId)) {
            taskCountByStatus.put(row.getStatus(), row.getTotal());
        }
        return new ProjectSummaryReportResponse(taskCountByStatus);
    }
}
