package com.projectmodule.timetracking.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.timetracking.domain.ActiveTimer;
import com.projectmodule.timetracking.domain.TimeEntry;
import com.projectmodule.timetracking.infrastructure.ActiveTimerRepository;
import com.projectmodule.timetracking.infrastructure.TimeEntryRepository;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging time on tasks, by hand or with a timer. Anyone who can work on a project's tasks can log
 * time on them, always as themselves. A person's timesheet shows only their own entries.
 */
@Service
public class TimeTrackingApplicationService {

    private static final int MAX_TIMESHEET_DAYS = 62;
    private static final int MINUTES_IN_A_DAY = 1440;

    private final TimeEntryRepository timeEntryRepository;
    private final ActiveTimerRepository activeTimerRepository;
    private final TaskApplicationService taskApplicationService;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public TimeTrackingApplicationService(TimeEntryRepository timeEntryRepository,
                                          ActiveTimerRepository activeTimerRepository,
                                          TaskApplicationService taskApplicationService,
                                          ProjectApplicationService projectApplicationService,
                                          ProjectAuthorizationService projectAuthorizationService) {
        this.timeEntryRepository = timeEntryRepository;
        this.activeTimerRepository = activeTimerRepository;
        this.taskApplicationService = taskApplicationService;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public TimeEntry logTime(RequestContext context, UUID taskId, LocalDate workDate, int minutes, String note,
                             boolean billable) {
        Task task = taskForWork(context, taskId);
        return timeEntryRepository.save(TimeEntry.log(task.getId(), task.getProjectId(),
                context.requireUserId().value(), workDate, minutes, note, billable));
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> listForTask(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.getTask(context, taskId);
        return timeEntryRepository.findByTaskIdOrderByWorkDateDescCreatedAtDesc(task.getId());
    }

    /** The caller's own entries in a date range, for their timesheet. */
    @Transactional(readOnly = true)
    public List<TimeEntry> listMine(RequestContext context, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(MAX_TIMESHEET_DAYS))) {
            throw new ValidationException("Choose a date range of at most " + MAX_TIMESHEET_DAYS + " days");
        }
        return timeEntryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscCreatedAtAsc(
                context.requireUserId().value(), from, to);
    }

    /** People remove their own entries; a project's managers can remove anyone's. */
    @Transactional
    public void deleteEntry(RequestContext context, UUID entryId) {
        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", entryId));
        projectApplicationService.findActiveProjectInCallerOrganization(context, entry.getProjectId());
        boolean own = entry.getUserId().equals(context.requireUserId().value());
        if (!own && !projectAuthorizationService.hasPermission(
                context.requireUserId(), entry.getProjectId(), ProjectPermission.EDIT_PROJECT)) {
            throw new AuthorizationException("You can only remove your own time entries");
        }
        timeEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public Optional<ActiveTimer> currentTimer(RequestContext context) {
        return activeTimerRepository.findById(context.requireUserId().value());
    }

    /** @throws ConflictException if the caller already has a timer running */
    @Transactional
    public ActiveTimer startTimer(RequestContext context, UUID taskId) {
        Task task = taskForWork(context, taskId);
        UUID userId = context.requireUserId().value();
        if (activeTimerRepository.existsById(userId)) {
            throw new ConflictException("You already have a timer running. Stop it first.");
        }
        return activeTimerRepository.save(new ActiveTimer(userId, task.getId(), Instant.now()));
    }

    /**
     * Stops the caller's timer and logs the time, rounded up to a whole minute. The browser supplies
     * the date, since "today" depends on where the person is; without one, the UTC date is used.
     */
    @Transactional
    public TimeEntry stopTimer(RequestContext context, LocalDate workDate, String note, boolean billable) {
        UUID userId = context.requireUserId().value();
        ActiveTimer timer = activeTimerRepository.findById(userId)
                .orElseThrow(() -> new ConflictException("You have no timer running"));
        Task task = taskApplicationService.getTask(context, timer.getTaskId());

        Instant now = Instant.now();
        long seconds = Duration.between(timer.getStartedAt(), now).getSeconds();
        int minutes = (int) Math.min(Math.max(1, (seconds + 59) / 60), MINUTES_IN_A_DAY);
        activeTimerRepository.delete(timer);
        return timeEntryRepository.save(TimeEntry.log(task.getId(), task.getProjectId(), userId,
                workDate != null ? workDate : LocalDate.ofInstant(now, ZoneOffset.UTC), minutes, note, billable));
    }

    /** Discards the caller's timer without logging anything. */
    @Transactional
    public void cancelTimer(RequestContext context) {
        activeTimerRepository.findById(context.requireUserId().value()).ifPresent(activeTimerRepository::delete);
    }

    private Task taskForWork(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.getTask(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_TASKS);
        return task;
    }
}
