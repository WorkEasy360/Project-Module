package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.domain.event.TaskOverdue;
import com.projectmodule.work.domain.event.TaskUpdated;
import com.projectmodule.project.domain.ProjectPriority;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A unit of work within a project, optionally under a {@link com.projectmodule.work.domain.TaskList}.
 *
 * <p>Plain field edits (name, description, due date) raise {@code task.updated}; only a status
 * transition or an assignment raises its own specific event, because
 * {@code docs/project/05-EVENTS.md} defines {@code task.completed}/{@code blocked}/
 * {@code overdue}/{@code assigned} as distinct from a plain update. There is no scheduler or
 * automated delay detection here: {@code BLOCKED} and {@code OVERDUE} are set explicitly by a
 * person, so manual workflows work fully without AI or automation.
 *
 * <p>The project is referenced by plain id, the same convention {@code ProjectMember} already
 * uses. There is no {@code taskListId}: {@code docs/project/03-DATABASE.md} does not associate
 * a Task with a Task List, only with a project.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int TAG_MAX_LENGTH = 40;

    /** Events raised since the last {@link #pullDomainEvents()}. Never persisted. */
    @Transient
    private final List<TaskDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ProjectPriority priority = ProjectPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence", nullable = false, length = 20)
    private TaskRecurrence recurrence = TaskRecurrence.NONE;

    // Eager: responses are mapped after the transaction ends, and tasks have only a few tags.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag", length = TAG_MAX_LENGTH)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Task() {
    }

    private Task(UUID id, UUID projectId, String name, LocalDate dueDate, ExternalUserId assigneeId) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        this.dueDate = dueDate;
        this.assigneeId = assigneeId == null ? null : assigneeId.value();
        this.status = TaskStatus.TODO;
        registerEvent(new TaskCreated(id));
    }

    /**
     * Creates a task in its initial, {@link TaskStatus#TODO} state.
     *
     * <p>An initial {@code assignee} does not raise {@code task.assigned}: creation is already
     * covered by {@code task.created}, the same reasoning {@code Milestone.create} uses for an
     * initial phase.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Task create(UUID projectId, String name, LocalDate dueDate, ExternalUserId assignee) {
        return new Task(UuidV7.generate(), projectId, name, dueDate, assignee);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void reschedule(LocalDate newDueDate) {
        requireNotArchived("reschedule");
        this.dueDate = newDueDate;
    }

    /**
     * Assigns the task to a user. Repeatable: reassignment is an ordinary, expected action, not
     * a one-way transition, so there is no guard against assigning to the same person again.
     */
    public void assign(ExternalUserId assignee) {
        requireNotArchived("assign");
        this.assigneeId = Objects.requireNonNull(assignee, "assignee is required").value();
        registerEvent(new TaskAssigned(getId()));
    }

    /**
     * Marks that this task was updated, for the audit and event pipeline. Deliberately separate
     * from the field mutators above, for the same reason {@code Phase.recordUpdated()} is.
     *
     * @throws BusinessRuleViolationException if the task is archived
     */
    public void recordUpdated() {
        requireNotArchived("record an update on");
        registerEvent(new TaskUpdated(getId()));
    }

    /** Sets the task's priority. A plain edit: the caller records the update. */
    public void prioritize(ProjectPriority newPriority) {
        requireNotArchived("set the priority of");
        this.priority = Objects.requireNonNull(newPriority, "priority is required");
    }

    /** Sets when work should begin. A plain edit: the caller records the update. */
    public void scheduleStart(LocalDate newStartDate) {
        requireNotArchived("schedule");
        this.startDate = newStartDate;
    }

    /** Sets the planned effort, in minutes. A plain edit: the caller records the update. */
    public void estimate(Integer minutes) {
        requireNotArchived("estimate");
        if (minutes != null && minutes < 0) {
            throw new ValidationException("Estimate must not be negative");
        }
        this.estimateMinutes = minutes;
    }

    /** Sets how the task repeats. A plain edit: the caller records the update. */
    public void repeat(TaskRecurrence newRecurrence) {
        requireNotArchived("set the recurrence of");
        this.recurrence = Objects.requireNonNull(newRecurrence, "recurrence is required");
    }

    /** Replaces the task's tags. Blank entries and duplicates, ignoring case, are dropped. */
    public void tag(Collection<String> newTags) {
        requireNotArchived("tag");
        Set<String> cleaned = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (String candidate : newTags) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if (trimmed.length() > TAG_MAX_LENGTH) {
                throw new ValidationException("A tag must be at most " + TAG_MAX_LENGTH + " characters");
            }
            if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                cleaned.add(trimmed);
            }
        }
        this.tags.clear();
        this.tags.addAll(cleaned);
    }

    /** Checks the start and due dates are in order. Called once all of an edit has been applied. */
    public void requireDatesInOrder() {
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw new ValidationException("Due date must not be before the start date");
        }
    }

    /**
     * Creates the task's next occurrence, if it repeats and has a date to repeat from, and stops
     * this one repeating, so reopening and completing it again cannot create a second one.
     */
    public Optional<Task> spawnNextOccurrence() {
        if (recurrence == TaskRecurrence.NONE || dueDate == null) {
            return Optional.empty();
        }
        Task next = new Task(UuidV7.generate(), projectId, name, recurrence.next(dueDate),
                getAssigneeId().orElse(null));
        next.description = description;
        next.priority = priority;
        next.startDate = startDate == null ? null : recurrence.next(startDate);
        next.estimateMinutes = estimateMinutes;
        next.recurrence = recurrence;
        next.tags.addAll(tags);
        this.recurrence = TaskRecurrence.NONE;
        return Optional.of(next);
    }

    /**
     * Marks that work has begun. This also brings back a task that was completed too early.
     *
     * @throws BusinessRuleViolationException if it is already in progress
     */
    public void startWork() {
        requireNotArchived("start");
        if (status == TaskStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException("Task is already in progress");
        }
        this.status = TaskStatus.IN_PROGRESS;
        registerEvent(new TaskUpdated(getId()));
    }

    /**
     * Puts the task back to the start, from any other status.
     *
     * @throws BusinessRuleViolationException if it is already there
     */
    public void reopen() {
        requireNotArchived("reopen");
        if (status == TaskStatus.TODO) {
            throw new BusinessRuleViolationException("Task is already to do");
        }
        this.status = TaskStatus.TODO;
        registerEvent(new TaskUpdated(getId()));
    }

    /**
     * Marks the task finished.
     *
     * @throws BusinessRuleViolationException if it is already completed
     */
    public void complete() {
        requireNotArchived("complete");
        if (status == TaskStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Task is already completed");
        }
        this.status = TaskStatus.COMPLETED;
        registerEvent(new TaskCompleted(getId()));
    }

    /**
     * Flags the task as blocked. {@link #startWork()} or {@link #reopen()} clears the flag.
     *
     * @throws BusinessRuleViolationException if it is already blocked, or already completed
     */
    public void markBlocked() {
        requireNotArchived("mark blocked");
        if (status == TaskStatus.BLOCKED) {
            throw new BusinessRuleViolationException("Task is already blocked");
        }
        if (status == TaskStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Cannot mark a completed task blocked");
        }
        this.status = TaskStatus.BLOCKED;
        registerEvent(new TaskBlocked(getId()));
    }

    /**
     * Flags the task as overdue. {@link #startWork()} or {@link #reopen()} clears the flag.
     *
     * @throws BusinessRuleViolationException if it is already overdue, or already completed
     */
    public void markOverdue() {
        requireNotArchived("mark overdue");
        if (status == TaskStatus.OVERDUE) {
            throw new BusinessRuleViolationException("Task is already overdue");
        }
        if (status == TaskStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Cannot mark a completed task overdue");
        }
        this.status = TaskStatus.OVERDUE;
        registerEvent(new TaskOverdue(getId()));
    }

    /**
     * Archives the task. Deleting a task means archiving it, consistent with {@code Project}.
     * Raises no event: {@code task.archived} is not in the current event catalogue.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Task is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived task");
        }
    }

    private void registerEvent(TaskDomainEvent event) {
        domainEvents.add(event);
    }

    /** Returns every event raised since the last call, and clears them. */
    public List<TaskDomainEvent> pullDomainEvents() {
        List<TaskDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Task name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Task name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<LocalDate> getDueDate() {
        return Optional.ofNullable(dueDate);
    }

    public Optional<ExternalUserId> getAssigneeId() {
        return Optional.ofNullable(assigneeId).map(ExternalUserId::of);
    }

    public TaskStatus getStatus() {
        return status;
    }

    public ProjectPriority getPriority() {
        return priority;
    }

    public Optional<LocalDate> getStartDate() {
        return Optional.ofNullable(startDate);
    }

    public Optional<Integer> getEstimateMinutes() {
        return Optional.ofNullable(estimateMinutes);
    }

    public TaskRecurrence getRecurrence() {
        return recurrence;
    }

    public List<String> getTags() {
        return tags.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
