package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.DelayedItemResponse;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.api.dto.DependencyResponse;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectHealthApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private RiskRepository riskRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private DelayDetectionApplicationService delayDetectionApplicationService;

    @Mock
    private DependencyAnalysisApplicationService dependencyAnalysisApplicationService;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ProjectHealthApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectHealthApplicationService(riskRepository, issueRepository,
                delayDetectionApplicationService, dependencyAnalysisApplicationService,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("all indicators zero when there is nothing to count")
    void allIndicatorsZeroWhenNothingToCount() {
        when(delayDetectionApplicationService.getDelayedItems(context, project.getId())).thenReturn(List.of());
        when(riskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());
        when(issueRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).thenReturn(0L);
        when(dependencyAnalysisApplicationService.getAnalysis(context, project.getId()))
                .thenReturn(new DependencyAnalysisResponse(List.of(), List.of(), List.of()));

        ProjectHealthResponse health = service.getHealth(context, project.getId());

        assertThat(health).isEqualTo(new ProjectHealthResponse(0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("each indicator reflects its underlying data, and no score/weight is derived")
    void indicatorsReflectUnderlyingData() {
        DelayedItemResponse delayedItem = new DelayedItemResponse(
                "TASK", UUID.randomUUID(), "Overdue task", LocalDate.of(2020, 1, 1), 100L);
        when(delayDetectionApplicationService.getDelayedItems(context, project.getId()))
                .thenReturn(List.of(delayedItem, delayedItem));

        when(riskRepository.countActiveByStatus(project.getId())).thenReturn(List.of(
                statusCount(RiskStatus.OPEN, 3L), statusCount(RiskStatus.RESOLVED, 5L)));

        when(issueRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).thenReturn(4L);

        UUID blockedTaskId = UUID.randomUUID();
        DependencyResponse brokenDependency = new DependencyResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, false, Instant.now(), Instant.now(), 0L);
        when(dependencyAnalysisApplicationService.getAnalysis(context, project.getId()))
                .thenReturn(new DependencyAnalysisResponse(
                        List.of(), List.of(blockedTaskId), List.of(brokenDependency)));

        ProjectHealthResponse health = service.getHealth(context, project.getId());

        assertThat(health.overdueItemCount()).isEqualTo(2L);
        assertThat(health.unresolvedRiskCount()).isEqualTo(3L);
        assertThat(health.unresolvedIssueCount()).isEqualTo(4L);
        assertThat(health.blockedTaskCount()).isEqualTo(1L);
        assertThat(health.brokenDependencyCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("calls the dependency analysis exactly once, reusing both fields from one result")
    void callsDependencyAnalysisExactlyOnce() {
        when(delayDetectionApplicationService.getDelayedItems(context, project.getId())).thenReturn(List.of());
        when(riskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());
        when(issueRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).thenReturn(0L);
        when(dependencyAnalysisApplicationService.getAnalysis(context, project.getId()))
                .thenReturn(new DependencyAnalysisResponse(List.of(), List.of(), List.of()));

        service.getHealth(context, project.getId());

        verify(dependencyAnalysisApplicationService, times(1)).getAnalysis(context, project.getId());
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getHealth(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }

    private static RiskRepository.StatusCount statusCount(RiskStatus status, long total) {
        return new RiskRepository.StatusCount() {
            @Override
            public RiskStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
