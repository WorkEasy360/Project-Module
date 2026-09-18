package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CalendarEntryResponse;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A project's calendar — active Task/Milestone (point-date) and Phase (ranged) entries within an
 * optional date window — per the approved {@code docs/project/17-CALENDAR-SPEC.md}.
 *
 * <p>Same authorization pattern as every other project-scoped read:
 * {@code ProjectApplicationService.findActiveProjectInCallerOrganization} then
 * {@code ProjectPermission.VIEW_PROJECT}, the same permission the plain list endpoints already
 * require.
 *
 * <p>Merged in the application layer, not a database {@code UNION} — the same approach
 * {@code SearchApplicationService} already established as this module's style for combining
 * results across entity types. Each repository's {@code ...DueDateIsNotNull...}/
 * {@code ...StartDateIsNotNull...} query already excludes items with no date to place on a
 * calendar; the optional {@code from}/{@code to} window is then applied here, in memory, rather
 * than pushed into the query — this sidesteps re-introducing the exact
 * "could not determine data type of parameter" PostgreSQL/Hibernate gap
 * {@code TaskRepository.findByFilters} already had to work around for an optional
 * {@code LocalDate} parameter.
 */
@Service
public class CalendarApplicationService {

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final PhaseRepository phaseRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public CalendarApplicationService(TaskRepository taskRepository,
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
    public List<CalendarEntryResponse> getCalendar(RequestContext context, UUID projectId,
                                                    LocalDate from, LocalDate to) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<CalendarEntryResponse> entries = new ArrayList<>();
        for (Task task : taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(projectId)) {
            LocalDate date = task.getDueDate().orElseThrow();
            if (withinWindow(date, from, to)) {
                entries.add(new CalendarEntryResponse("TASK", task.getId(), task.getName(), date, null, null));
            }
        }
        for (Milestone milestone
                : milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(projectId)) {
            LocalDate date = milestone.getDueDate().orElseThrow();
            if (withinWindow(date, from, to)) {
                entries.add(new CalendarEntryResponse("MILESTONE", milestone.getId(), milestone.getName(),
                        date, null, null));
            }
        }
        for (Phase phase : phaseRepository
                .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(projectId)) {
            LocalDate startDate = phase.getStartDate().orElseThrow();
            LocalDate endDate = phase.getEndDate().orElseThrow();
            if (overlapsWindow(startDate, endDate, from, to)) {
                entries.add(new CalendarEntryResponse("PHASE", phase.getId(), phase.getName(),
                        null, startDate, endDate));
            }
        }

        entries.sort(Comparator
                .comparing(CalendarApplicationService::primaryDate)
                .thenComparing(CalendarEntryResponse::name));
        return entries;
    }

    private static LocalDate primaryDate(CalendarEntryResponse entry) {
        return entry.date() != null ? entry.date() : entry.startDate();
    }

    private static boolean withinWindow(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static boolean overlapsWindow(LocalDate startDate, LocalDate endDate, LocalDate from, LocalDate to) {
        return (from == null || !endDate.isBefore(from)) && (to == null || !startDate.isAfter(to));
    }
}
