package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.projectmodule.work.api.DependencyMapper;
import com.projectmodule.work.api.dto.GanttResponse;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.DependencyRepository;
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
class GanttApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private PhaseRepository phaseRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private DependencyRepository dependencyRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private GanttApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new GanttApplicationService(phaseRepository, taskRepository, milestoneRepository,
                dependencyRepository, new DependencyMapper(), projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("renders a scheduled phase as a bar, an unscheduled one is excluded")
    void rendersPhaseAsBar() {
        Phase scheduled = Phase.create(project.getId(), "Design");
        scheduled.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        when(phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of(scheduled));
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId()))
                .thenReturn(List.of());

        GanttResponse gantt = service.getGantt(context, project.getId());

        assertThat(gantt.phases()).hasSize(1);
        assertThat(gantt.phases().get(0).name()).isEqualTo("Design");
        assertThat(gantt.phases().get(0).startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(gantt.phases().get(0).endDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("renders a task as a point event at its dueDate, not a bar")
    void rendersTaskAsPoint() {
        Task task = Task.create(project.getId(), "Implement login", LocalDate.of(2026, 3, 1), null);

        when(phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(task));
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of(task));
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId()))
                .thenReturn(List.of());
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of());

        GanttResponse gantt = service.getGantt(context, project.getId());

        assertThat(gantt.tasks()).hasSize(1);
        assertThat(gantt.tasks().get(0).name()).isEqualTo("Implement login");
        assertThat(gantt.tasks().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("renders a milestone as a point event at its dueDate")
    void rendersMilestoneAsPoint() {
        Milestone milestone = Milestone.create(project.getId(), null, "Kickoff", LocalDate.of(2026, 1, 15));

        when(phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId()))
                .thenReturn(List.of(milestone));

        GanttResponse gantt = service.getGantt(context, project.getId());

        assertThat(gantt.milestones()).hasSize(1);
        assertThat(gantt.milestones().get(0).name()).isEqualTo("Kickoff");
        assertThat(gantt.milestones().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("exposes existing task dependency edges, including broken ones")
    void exposesDependencyEdges() {
        Task dependent = Task.create(project.getId(), "Dependent", null, null);
        Task prerequisite = Task.create(project.getId(), "Prerequisite", null, null);
        Dependency dependency = Dependency.create(dependent.getId(), prerequisite.getId());
        dependency.markBroken();

        when(phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(dependent, prerequisite));
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                        project.getId()))
                .thenReturn(List.of());
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(dependency));

        GanttResponse gantt = service.getGantt(context, project.getId());

        assertThat(gantt.dependencies()).hasSize(1);
        assertThat(gantt.dependencies().get(0).dependentTaskId()).isEqualTo(dependent.getId());
        assertThat(gantt.dependencies().get(0).prerequisiteTaskId()).isEqualTo(prerequisite.getId());
        assertThat(gantt.dependencies().get(0).broken()).isTrue();
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getGantt(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }
}
