package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.DependencyMapper;
import com.projectmodule.work.api.dto.DependencyResponse;
import com.projectmodule.work.api.dto.GanttBarResponse;
import com.projectmodule.work.api.dto.GanttPointResponse;
import com.projectmodule.work.api.dto.GanttResponse;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.DependencyRepository;
import com.projectmodule.work.infrastructure.MilestoneRepository;
import com.projectmodule.work.infrastructure.PhaseRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's Gantt view, per the approved {@code docs/project/19-GANTT-SPEC.md}: Phase rendered
 * as a real bar (it is the one entity with a real duration), Task/Milestone rendered as point
 * events at their {@code dueDate} rather than a fabricated bar, plus the existing Task
 * {@code Dependency} edges exposed for the frontend to draw arrows between task points.
 *
 * <p>Reuses the exact repository queries {@code CalendarApplicationService} already added — no
 * new repository query method is needed for Gantt at all. Same authorization pattern as every
 * other project-scoped read.
 */
@Service
public class GanttApplicationService {

    private final PhaseRepository phaseRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final DependencyRepository dependencyRepository;
    private final DependencyMapper dependencyMapper;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public GanttApplicationService(PhaseRepository phaseRepository,
                                   TaskRepository taskRepository,
                                   MilestoneRepository milestoneRepository,
                                   DependencyRepository dependencyRepository,
                                   DependencyMapper dependencyMapper,
                                   ProjectApplicationService projectApplicationService,
                                   ProjectAuthorizationService projectAuthorizationService) {
        this.phaseRepository = phaseRepository;
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.dependencyRepository = dependencyRepository;
        this.dependencyMapper = dependencyMapper;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public GanttResponse getGantt(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<GanttBarResponse> phases = phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                        projectId)
                .stream()
                .map(GanttApplicationService::toBar)
                .toList();

        List<Task> projectTasks = taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);

        List<GanttPointResponse> tasks = taskRepository
                .findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(projectId)
                .stream()
                .map(task -> new GanttPointResponse(task.getId(), task.getName(), task.getDueDate().orElseThrow()))
                .toList();

        List<GanttPointResponse> milestones = milestoneRepository
                .findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(projectId)
                .stream()
                .map(milestone -> new GanttPointResponse(
                        milestone.getId(), milestone.getName(), milestone.getDueDate().orElseThrow()))
                .toList();

        Set<UUID> projectTaskIds = projectTasks.stream().map(Task::getId).collect(Collectors.toSet());
        List<DependencyResponse> dependencies = projectTaskIds.isEmpty()
                ? List.of()
                : dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(projectTaskIds).stream()
                        .map(dependencyMapper::toResponse)
                        .toList();

        return new GanttResponse(phases, tasks, milestones, dependencies);
    }

    private static GanttBarResponse toBar(Phase phase) {
        return new GanttBarResponse(phase.getId(), phase.getName(),
                phase.getStartDate().orElseThrow(), phase.getEndDate().orElseThrow());
    }
}
