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
class TimelineApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private PhaseRepository phaseRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private TimelineApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new TimelineApplicationService(phaseRepository, milestoneRepository, taskRepository,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("nests a milestone under its phase, ordered by startDate/dueDate")
    void nestsMilestoneUnderItsPhase() {
        Phase later = Phase.create(project.getId(), "Later phase");
        later.schedule(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        Phase earlier = Phase.create(project.getId(), "Earlier phase");
        earlier.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        Milestone milestone = Milestone.create(project.getId(), earlier.getId(), "Kickoff", LocalDate.of(2026, 1, 15));

        when(phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(later, earlier));
        when(milestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(milestone));
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());

        TimelineResult result = service.getTimeline(context, project.getId());

        assertThat(result.phases()).extracting(g -> g.phase().getName())
                .containsExactly("Earlier phase", "Later phase");
        assertThat(result.phases().get(0).milestones()).containsExactly(milestone);
        assertThat(result.phases().get(1).milestones()).isEmpty();
    }

    @Test
    @DisplayName("omits a milestone with no phaseId from every phase's milestones")
    void omitsUnphasedMilestone() {
        Phase phase = Phase.create(project.getId(), "Phase");
        phase.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        Milestone unphased = Milestone.create(project.getId(), null, "Unphased", LocalDate.of(2026, 1, 15));

        when(phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(phase));
        when(milestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(unphased));
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());

        TimelineResult result = service.getTimeline(context, project.getId());

        assertThat(result.phases().get(0).milestones()).isEmpty();
    }

    @Test
    @DisplayName("returns tasks as a flat list ordered by dueDate, nulls last")
    void returnsTasksFlatOrderedByDueDate() {
        Task noDueDate = Task.create(project.getId(), "No due date", null, null);
        Task later = Task.create(project.getId(), "Later task", LocalDate.of(2026, 6, 1), null);
        Task earlier = Task.create(project.getId(), "Earlier task", LocalDate.of(2026, 1, 1), null);

        when(phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(noDueDate, later, earlier));

        TimelineResult result = service.getTimeline(context, project.getId());

        assertThat(result.tasks()).containsExactly(earlier, later, noDueDate);
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getTimeline(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }
}
