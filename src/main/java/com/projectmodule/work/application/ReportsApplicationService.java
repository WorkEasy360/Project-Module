package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.DecisionRepository;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's summary report, per the approved {@code docs/project/24-REPORTS-SPEC.md} — the
 * same enum-distribution, zero-filled-map pattern {@code DashboardApplicationService} already
 * establishes for the organization-wide dashboard, scoped here to one project instead.
 *
 * <p>Unlike {@code DashboardApplicationService} (deliberately not gated by
 * {@code ProjectAuthorizationService}, since it spans every project in the organization), this
 * report is scoped to one project and therefore does use the same {@code VIEW_PROJECT} check
 * every other project-scoped read in this module already has.
 *
 * <p>{@code delayedItemCount}/{@code brokenDependencyCount} are not re-derived business logic —
 * they delegate to {@link DelayDetectionApplicationService}/
 * {@link DependencyAnalysisApplicationService} and take the size of their result lists, so the
 * definition of "delayed"/"broken" lives in exactly one place.
 */
@Service
public class ReportsApplicationService {

    private final TaskRepository taskRepository;
    private final RiskRepository riskRepository;
    private final IssueRepository issueRepository;
    private final DecisionRepository decisionRepository;
    private final DelayDetectionApplicationService delayDetectionApplicationService;
    private final DependencyAnalysisApplicationService dependencyAnalysisApplicationService;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ReportsApplicationService(TaskRepository taskRepository,
                                     RiskRepository riskRepository,
                                     IssueRepository issueRepository,
                                     DecisionRepository decisionRepository,
                                     DelayDetectionApplicationService delayDetectionApplicationService,
                                     DependencyAnalysisApplicationService dependencyAnalysisApplicationService,
                                     ProjectApplicationService projectApplicationService,
                                     ProjectAuthorizationService projectAuthorizationService) {
        this.taskRepository = taskRepository;
        this.riskRepository = riskRepository;
        this.issueRepository = issueRepository;
        this.decisionRepository = decisionRepository;
        this.delayDetectionApplicationService = delayDetectionApplicationService;
        this.dependencyAnalysisApplicationService = dependencyAnalysisApplicationService;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public ProjectSummaryReportResponse getSummary(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        Map<TaskStatus, Long> taskCountByStatus = zeroFilled(TaskStatus.class);
        for (TaskRepository.StatusCount row : taskRepository.countActiveByStatus(projectId)) {
            taskCountByStatus.put(row.getStatus(), row.getTotal());
        }

        Map<RiskStatus, Long> riskCountByStatus = zeroFilled(RiskStatus.class);
        for (RiskRepository.StatusCount row : riskRepository.countActiveByStatus(projectId)) {
            riskCountByStatus.put(row.getStatus(), row.getTotal());
        }

        Map<ProjectPriority, Long> riskCountByPriority = zeroFilled(ProjectPriority.class);
        for (RiskRepository.PriorityCount row : riskRepository.countActiveByPriority(projectId)) {
            riskCountByPriority.put(row.getPriority(), row.getTotal());
        }

        Map<ProjectPriority, Long> issueCountByPriority = zeroFilled(ProjectPriority.class);
        for (IssueRepository.PriorityCount row : issueRepository.countActiveByPriority(projectId)) {
            issueCountByPriority.put(row.getPriority(), row.getTotal());
        }

        long decisionCount = decisionRepository.countByProjectIdAndArchivedAtIsNull(projectId);
        long delayedItemCount = delayDetectionApplicationService.getDelayedItems(context, projectId).size();
        long brokenDependencyCount =
                dependencyAnalysisApplicationService.getAnalysis(context, projectId).brokenDependencies().size();

        return new ProjectSummaryReportResponse(taskCountByStatus, riskCountByStatus, riskCountByPriority,
                issueCountByPriority, decisionCount, delayedItemCount, brokenDependencyCount);
    }

    private static <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> enumType) {
        Map<E, Long> map = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            map.put(value, 0L);
        }
        return map;
    }
}
