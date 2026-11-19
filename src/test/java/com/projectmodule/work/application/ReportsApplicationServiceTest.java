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
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.TaskRepository;
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
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ReportsApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ReportsApplicationService(taskRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("every status is present, zero-filled when there is no matching row")
    void everyStatusPresentZeroFilled() {
        when(taskRepository.countActiveByStatus(project.getId())).thenReturn(List.of());

        ProjectSummaryReportResponse summary = service.getSummary(context, project.getId());

        assertThat(summary.taskCountByStatus()).hasSize(TaskStatus.values().length);
        assertThat(summary.taskCountByStatus().values()).allMatch(v -> v == 0L);
    }

    @Test
    @DisplayName("fills counts from repository rows")
    void fillsCounts() {
        when(taskRepository.countActiveByStatus(project.getId())).thenReturn(
                List.of(countRow(TaskStatus.BLOCKED, 3L), countRow(TaskStatus.COMPLETED, 5L)));

        ProjectSummaryReportResponse summary = service.getSummary(context, project.getId());

        assertThat(summary.taskCountByStatus().get(TaskStatus.BLOCKED)).isEqualTo(3L);
        assertThat(summary.taskCountByStatus().get(TaskStatus.COMPLETED)).isEqualTo(5L);
        assertThat(summary.taskCountByStatus().get(TaskStatus.TODO)).isZero();
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
