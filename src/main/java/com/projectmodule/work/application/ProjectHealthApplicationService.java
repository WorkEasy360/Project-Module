package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's health indicators, per the approved {@code docs/project/21-PROJECT-HEALTH-SPEC.md}
 * — deterministic counts only, no weighted score or formula.
 *
 * <p>Same authorization pattern as every other project-scoped read. No persistence: every count
 * is derived on read, the same pattern {@code DelayDetectionApplicationService}/
 * {@code DependencyAnalysisApplicationService}/{@code ReportsApplicationService} already
 * establish. {@code overdueItemCount}/{@code blockedTaskCount}/{@code brokenDependencyCount} are
 * not re-derived business logic — they delegate to
 * {@link DelayDetectionApplicationService}/{@link DependencyAnalysisApplicationService} so the
 * definition of "delayed"/"blocked"/"broken" lives in exactly one place. The dependency analysis
 * is fetched exactly once and both counts are read from that one result.
 */
@Service
public class ProjectHealthApplicationService {

    private final RiskRepository riskRepository;
    private final IssueRepository issueRepository;
    private final DelayDetectionApplicationService delayDetectionApplicationService;
    private final DependencyAnalysisApplicationService dependencyAnalysisApplicationService;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectHealthApplicationService(RiskRepository riskRepository,
                                           IssueRepository issueRepository,
                                           DelayDetectionApplicationService delayDetectionApplicationService,
                                           DependencyAnalysisApplicationService dependencyAnalysisApplicationService,
                                           ProjectApplicationService projectApplicationService,
                                           ProjectAuthorizationService projectAuthorizationService) {
        this.riskRepository = riskRepository;
        this.issueRepository = issueRepository;
        this.delayDetectionApplicationService = delayDetectionApplicationService;
        this.dependencyAnalysisApplicationService = dependencyAnalysisApplicationService;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public ProjectHealthResponse getHealth(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        long overdueItemCount = delayDetectionApplicationService.getDelayedItems(context, projectId).size();

        long unresolvedRiskCount = riskRepository.countActiveByStatus(projectId).stream()
                .filter(row -> row.getStatus() == RiskStatus.OPEN)
                .mapToLong(RiskRepository.StatusCount::getTotal)
                .findFirst()
                .orElse(0L);

        long unresolvedIssueCount = issueRepository.countByProjectIdAndArchivedAtIsNull(projectId);

        DependencyAnalysisResponse dependencyAnalysis =
                dependencyAnalysisApplicationService.getAnalysis(context, projectId);
        long blockedTaskCount = dependencyAnalysis.blockedTaskIds().size();
        long brokenDependencyCount = dependencyAnalysis.brokenDependencies().size();

        return new ProjectHealthResponse(
                overdueItemCount, unresolvedRiskCount, unresolvedIssueCount, blockedTaskCount, brokenDependencyCount);
    }
}
