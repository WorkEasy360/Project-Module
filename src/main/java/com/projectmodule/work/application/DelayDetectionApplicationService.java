package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's delayed Task/Milestone/Phase items, per the approved
 * {@code docs/project/22-DELAY-DETECTION-SPEC.md}. Purely a deterministic, date-based read: it
 * never sets {@code Task.status}/{@code Milestone.status} to a delayed-looking value and never
 * mutates anything — {@code TaskStatus}'s own javadoc already states there is no automated delay
 * detection at the entity level, and this service is that separate, read-only P3 capability.
 *
 * <p>Same authorization pattern as every other project-scoped read. Merged in the application
 * layer, not a database {@code UNION} — the same approach {@code SearchApplicationService}/
 * {@code CalendarApplicationService} already established.
 */
@Service
public class DelayDetectionApplicationService {

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final PhaseRepository phaseRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public DelayDetectionApplicationService(TaskRepository taskRepository,
                                            MilestoneRepository milestoneRepository,
                                            PhaseRepository phaseRepository,
                                            ProjectApplicationService projectApplicationService,
                                            ProjectAuthorizationService projectAuthorizationService) {
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.phaseRepository = phaseRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<DelayedItemResponse> getDelayedItems(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        LocalDate today = LocalDate.now();
        List<DelayedItemResponse> items = new ArrayList<>();

        for (Task task : taskRepository
                .findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                        projectId, today, TaskStatus.COMPLETED)) {
            LocalDate dueDate = task.getDueDate().orElseThrow();
            items.add(new DelayedItemResponse("TASK", task.getId(), task.getName(), dueDate,
                    daysBetween(dueDate, today)));
        }
        for (Milestone milestone : milestoneRepository
                .findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                        projectId, today, MilestoneStatus.COMPLETED)) {
            LocalDate dueDate = milestone.getDueDate().orElseThrow();
            items.add(new DelayedItemResponse("MILESTONE", milestone.getId(), milestone.getName(), dueDate,
                    daysBetween(dueDate, today)));
        }
        for (Phase phase : phaseRepository.findByProjectIdAndEndDateBeforeAndArchivedAtIsNullOrderByEndDateAsc(
                projectId, today)) {
            LocalDate dueDate = phase.getEndDate().orElseThrow();
            items.add(new DelayedItemResponse("PHASE", phase.getId(), phase.getName(), dueDate,
                    daysBetween(dueDate, today)));
        }

        items.sort(Comparator.comparing(DelayedItemResponse::dueDate).thenComparing(DelayedItemResponse::name));
        return items;
    }

    private static long daysBetween(LocalDate dueDate, LocalDate today) {
        return ChronoUnit.DAYS.between(dueDate, today);
    }
}
