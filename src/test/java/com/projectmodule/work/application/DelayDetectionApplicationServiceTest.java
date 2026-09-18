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
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.MilestoneRepository;
import com.projectmodule.work.infrastructure.PhaseRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
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
class DelayDetectionApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private PhaseRepository phaseRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private DelayDetectionApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new DelayDetectionApplicationService(taskRepository, milestoneRepository, phaseRepository,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("merges delayed tasks, milestones and phases, ordered by dueDate ascending")
    void mergesAllThreeEntityTypesOrderedByDueDate() {
        LocalDate today = LocalDate.now();
        Task task = Task.create(project.getId(), "Delayed task", today.minusDays(5), null);
        Milestone milestone = Milestone.create(project.getId(), null, "Delayed milestone", today.minusDays(10));
        Phase phase = Phase.create(project.getId(), "Delayed phase");
        phase.schedule(today.minusDays(30), today.minusDays(2));

        when(taskRepository.findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId(), today, TaskStatus.COMPLETED))
                .thenReturn(List.of(task));
        when(milestoneRepository.findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId(), today, MilestoneStatus.COMPLETED))
                .thenReturn(List.of(milestone));
        when(phaseRepository.findByProjectIdAndEndDateBeforeAndArchivedAtIsNullOrderByEndDateAsc(
                        project.getId(), today))
                .thenReturn(List.of(phase));

        List<DelayedItemResponse> items = service.getDelayedItems(context, project.getId());

        assertThat(items).extracting(DelayedItemResponse::entityType)
                .containsExactly("MILESTONE", "TASK", "PHASE");
        assertThat(items).extracting(DelayedItemResponse::daysOverdue)
                .containsExactly(10L, 5L, 2L);
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getDelayedItems(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }
}
