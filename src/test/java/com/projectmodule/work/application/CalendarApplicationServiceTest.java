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
import com.projectmodule.work.api.dto.CalendarEntryResponse;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
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
class CalendarApplicationServiceTest {

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

    private CalendarApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new CalendarApplicationService(taskRepository, milestoneRepository, phaseRepository,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("merges tasks, milestones and phases into one date-ordered list")
    void mergesAllThreeEntityTypes() {
        Task task = Task.create(project.getId(), "Task entry", LocalDate.of(2026, 3, 10), null);
        Milestone milestone = Milestone.create(project.getId(), null, "Milestone entry", LocalDate.of(2026, 3, 5));
        Phase phase = Phase.create(project.getId(), "Phase entry");
        phase.schedule(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20));

        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of(task));
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of(milestone));
        when(phaseRepository.findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of(phase));

        List<CalendarEntryResponse> entries = service.getCalendar(context, project.getId(), null, null);

        assertThat(entries).extracting(CalendarEntryResponse::entityType)
                .containsExactly("PHASE", "MILESTONE", "TASK");
        assertThat(entries).extracting(CalendarEntryResponse::name)
                .containsExactly("Phase entry", "Milestone entry", "Task entry");
    }

    @Test
    @DisplayName("excludes point-date items outside the requested window")
    void excludesItemsOutsideWindow() {
        Task inWindow = Task.create(project.getId(), "In window", LocalDate.of(2026, 3, 10), null);
        Task outOfWindow = Task.create(project.getId(), "Out of window", LocalDate.of(2026, 6, 1), null);
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of(inWindow, outOfWindow));
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(phaseRepository.findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of());

        List<CalendarEntryResponse> entries = service.getCalendar(
                context, project.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(entries).extracting(CalendarEntryResponse::name).containsExactly("In window");
    }

    @Test
    @DisplayName("includes a phase whose range only partially overlaps the window")
    void includesPartiallyOverlappingPhase() {
        Phase phase = Phase.create(project.getId(), "Spanning phase");
        phase.schedule(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15));
        when(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(project.getId()))
                .thenReturn(List.of());
        when(phaseRepository.findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        project.getId()))
                .thenReturn(List.of(phase));

        List<CalendarEntryResponse> entries = service.getCalendar(
                context, project.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(entries).extracting(CalendarEntryResponse::name).containsExactly("Spanning phase");
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getCalendar(context, project.getId(), null, null))
                .isInstanceOf(AuthorizationException.class);
    }
}
