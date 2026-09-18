package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.MilestoneRepository;
import com.projectmodule.work.infrastructure.PhaseRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's timeline — active phases (each with their nested milestones) plus a flat,
 * project-level task list — per the approved {@code docs/project/18-TIMELINE-SPEC.md}.
 *
 * <p>Same authorization pattern as every other project-scoped read.
 *
 * <p>Reuses the exact same three existing, unmodified
 * {@code findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc} repository calls
 * {@code PhaseApplicationService}/{@code MilestoneApplicationService}/{@code TaskApplicationService}
 * already use, then re-sorts by date in memory (nulls last) — Timeline shows the whole project,
 * unlike Calendar, which excludes undated items and windows by date (§4/§1 of the spec). A
 * milestone with no {@code phaseId}, or whose phase is archived (and therefore excluded from
 * {@code phases}), is simply omitted from every phase's {@code milestones} — see spec §7.
 */
@Service
public class TimelineApplicationService {

    private final PhaseRepository phaseRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public TimelineApplicationService(PhaseRepository phaseRepository,
                                      MilestoneRepository milestoneRepository,
                                      TaskRepository taskRepository,
                                      ProjectApplicationService projectApplicationService,
                                      ProjectAuthorizationService projectAuthorizationService) {
        this.phaseRepository = phaseRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public TimelineResult getTimeline(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<Phase> phases = new ArrayList<>(
                phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId));
        phases.sort(Comparator.comparing(TimelineApplicationService::phaseStartDate));

        List<Milestone> milestones = milestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
        Map<UUID, List<Milestone>> milestonesByPhase = milestones.stream()
                .filter(m -> m.getPhaseId().isPresent())
                .collect(Collectors.groupingBy(m -> m.getPhaseId().get()));

        List<TimelinePhaseGroup> phaseGroups = new ArrayList<>();
        for (Phase phase : phases) {
            List<Milestone> phaseMilestones = new ArrayList<>(
                    milestonesByPhase.getOrDefault(phase.getId(), List.of()));
            phaseMilestones.sort(Comparator.comparing(TimelineApplicationService::milestoneDueDate));
            phaseGroups.add(new TimelinePhaseGroup(phase, phaseMilestones));
        }

        List<Task> tasks = new ArrayList<>(
                taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId));
        tasks.sort(Comparator.comparing(TimelineApplicationService::taskDueDate));

        return new TimelineResult(phaseGroups, tasks);
    }

    private static final LocalDate FAR_FUTURE = LocalDate.MAX;

    private static LocalDate phaseStartDate(Phase phase) {
        return phase.getStartDate().orElse(FAR_FUTURE);
    }

    private static LocalDate milestoneDueDate(Milestone milestone) {
        return milestone.getDueDate().orElse(FAR_FUTURE);
    }

    private static LocalDate taskDueDate(Task task) {
        return task.getDueDate().orElse(FAR_FUTURE);
    }
}
