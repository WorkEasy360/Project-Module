package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
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
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.DecisionRepository;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
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
class ReportsApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RiskRepository riskRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private DelayDetectionApplicationService delayDetectionApplicationService;

    @Mock
    private DependencyAnalysisApplicationService dependencyAnalysisApplicationService;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ReportsApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ReportsApplicationService(taskRepository, riskRepository, issueRepository, decisionRepository,
                delayDetectionApplicationService, dependencyAnalysisApplicationService,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("every enum key is present, zero-filled when there is no matching row")
    void everyEnumKeyPresentZeroFilled() {
        when(taskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());
        when(riskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());
        when(riskRepository.countActiveByPriority(project.getId())).thenReturn(List.of());
        when(issueRepository.countActiveByPriority(project.getId())).thenReturn(List.of());
        when(decisionRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).thenReturn(0L);
        when(delayDetectionApplicationService.getDelayedItems(context, project.getId())).thenReturn(List.of());
        when(dependencyAnalysisApplicationService.getAnalysis(context, project.getId()))
                .thenReturn(new DependencyAnalysisResponse(List.of(), List.of(), List.of()));

        ProjectSummaryReportResponse summary = service.getSummary(context, project.getId());

        assertThat(summary.taskCountByStatus()).hasSize(TaskStatus.values().length);
        assertThat(summary.taskCountByStatus().values()).allMatch(v -> v == 0L);
        assertThat(summary.riskCountByStatus()).hasSize(RiskStatus.values().length);
        assertThat(summary.riskCountByPriority()).hasSize(ProjectPriority.values().length);
        assertThat(summary.issueCountByPriority()).hasSize(ProjectPriority.values().length);
        assertThat(summary.decisionCount()).isZero();
        assertThat(summary.delayedItemCount()).isZero();
        assertThat(summary.brokenDependencyCount()).isZero();
    }

    @Test
    @DisplayName("fills counts from repository rows and delegates delayed/broken counts")
    void fillsCountsAndDelegates() {
        when(taskRepository.countActiveByStatus(project.getId())).thenReturn(
                List.of(countRow(TaskStatus.BLOCKED, 3L)));
        when(riskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());
        when(riskRepository.countActiveByPriority(project.getId())).thenReturn(List.of());
        when(issueRepository.countActiveByPriority(project.getId())).thenReturn(List.of());
        when(decisionRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).thenReturn(7L);
        DelayedItemResponse delayedItem = new DelayedItemResponse(
                "TASK", UUID.randomUUID(), "Overdue task", LocalDate.now().minusDays(1), 1L);
        when(delayDetectionApplicationService.getDelayedItems(context, project.getId()))
                .thenReturn(List.of(delayedItem));
        UUID taskId = UUID.randomUUID();
        DependencyResponse brokenDependency = new DependencyResponse(
                UUID.randomUUID(), taskId, UUID.randomUUID(), true, false, Instant.now(), Instant.now(), 0L);
        when(dependencyAnalysisApplicationService.getAnalysis(context, project.getId()))
                .thenReturn(new DependencyAnalysisResponse(List.of(), List.of(taskId), List.of(brokenDependency)));

        ProjectSummaryReportResponse summary = service.getSummary(context, project.getId());

        assertThat(summary.taskCountByStatus().get(TaskStatus.BLOCKED)).isEqualTo(3L);
        assertThat(summary.decisionCount()).isEqualTo(7L);
        assertThat(summary.delayedItemCount()).isEqualTo(1L);
        assertThat(summary.brokenDependencyCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getSummary(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }

    private static TaskRepository.StatusCount countRow(TaskStatus status, long total) {
        return new TaskRepository.StatusCount() {
            @Override
            public TaskStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
