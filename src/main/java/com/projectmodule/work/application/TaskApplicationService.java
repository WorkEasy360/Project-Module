package com.projectmodule.work.application;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskSortField;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for task CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link PhaseApplicationService}: the owning project is
 * resolved through {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and
 * every mutation is gated by the existing {@link ProjectAuthorizationService} — no second
 * authorization system.
 *
 * <p>Status transitions ({@code complete}/{@code markBlocked}/{@code markOverdue}) and
 * assignment go through the same {@code PATCH} this service uses for plain field edits, since
 * {@code docs/project/04-API.md} defines no separate action endpoint for a task. A plain field
 * edit raises {@code task.updated}; a status transition or assignment raises exactly the one
 * event {@code docs/project/05-EVENTS.md} defines for it — see {@link Task}.
 */
@Service
public class TaskApplicationService {

    private final TaskRepository taskRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final WorkEventRecorder workEventRecorder;

    public TaskApplicationService(TaskRepository taskRepository,
                                  ProjectApplicationService projectApplicationService,
                                  ProjectAuthorizationService projectAuthorizationService,
                                  WorkEventRecorder workEventRecorder) {
        this.taskRepository = taskRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.workEventRecorder = workEventRecorder;
    }

    @Transactional
    public Task createTask(RequestContext context, UUID projectId, CreateTaskRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        ExternalUserId assignee = request.assigneeId() == null
                ? null : ExternalUserId.of(request.assigneeId());
        Task task = Task.create(projectId, request.name(), request.dueDate(), assignee);
        task.describe(request.description());

        task = taskRepository.save(task);
        recordEvents(context, task);

        return task;
    }

    @Transactional(readOnly = true)
    public Task getTask(RequestContext context, UUID taskId) {
        Task task = findActiveTask(taskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, task.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return task;
    }

    @Transactional(readOnly = true)
    public List<Task> listTasks(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    /**
     * Lists tasks in the project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}, in the order requested per
     * {@code docs/project/15-LIST-SPEC.md}. Any filter parameter left {@code null} is not
     * applied. {@code archived}: {@code false} returns active tasks only (the same default
     * {@link #listTasks} above already has); {@code true} returns archived tasks only.
     * {@code sortBy}/{@code sortDir} default to {@code CREATED_AT}/{@code ASC} when the caller
     * passes {@code null} — reproducing {@link #listTasks(RequestContext, UUID)}'s fixed order
     * exactly.
     */
    @Transactional(readOnly = true)
    public List<Task> listTasks(RequestContext context, UUID projectId, TaskStatus status, UUID assigneeId,
                                LocalDate dueDateBefore, LocalDate dueDateAfter, boolean archived,
                                TaskSortField sortBy, SortDirection sortDir) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return taskRepository.findByFilters(projectId, status, assigneeId, dueDateBefore, dueDateAfter, archived,
                toSort(sortBy, sortDir));
    }

    private static Sort toSort(TaskSortField sortBy, SortDirection sortDir) {
        Sort.Direction direction = sortDir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortBy == null ? TaskSortField.CREATED_AT : sortBy) {
            case CREATED_AT -> "createdAt";
            case DUE_DATE -> "dueDate";
            case NAME -> "name";
            case STATUS -> "status";
        };
        return Sort.by(direction, property);
    }

    /**
     * The project's Kanban board, per {@code docs/project/16-KANBAN-SPEC.md}: active tasks
     * grouped by {@code status}, every {@link TaskStatus} column present (even empty), each
     * column ordered by {@code createdAt} ascending — one query
     * ({@link #listTasks(RequestContext, UUID)}'s underlying repository call), grouped here in
     * memory rather than one query per column.
     */
    @Transactional(readOnly = true)
    public Map<TaskStatus, List<Task>> getBoard(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<Task> tasks = taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
        Map<TaskStatus, List<Task>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));

        Map<TaskStatus, List<Task>> board = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            board.put(status, grouped.getOrDefault(status, List.of()));
        }
        return board;
    }

    @Transactional
    public Task updateTask(RequestContext context, UUID taskId, UpdateTaskRequest request) {
        Task task = findActiveTask(taskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, task.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (task.getVersion() != request.version()) {
            throw new ConflictException(
                    "Task " + taskId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(task, request);

        task = taskRepository.save(task);
        recordEvents(context, task);

        return task;
    }

    @Transactional
    public void archiveTask(RequestContext context, UUID taskId) {
        Task task = findActiveTask(taskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, task.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        task.archive(context.requireUserId());
        taskRepository.save(task);
        // No event: task.archived is not in the current event catalogue.
    }

    private void applyUpdates(Task task, UpdateTaskRequest request) {
        boolean plainFieldChanged = false;

        if (request.name() != null) {
            task.rename(request.name());
            plainFieldChanged = true;
        }
        if (request.description() != null) {
            task.describe(request.description());
            plainFieldChanged = true;
        }
        if (request.dueDate() != null) {
            task.reschedule(request.dueDate());
            plainFieldChanged = true;
        }
        if (plainFieldChanged) {
            task.recordUpdated();
        }
        if (request.assigneeId() != null) {
            task.assign(ExternalUserId.of(request.assigneeId()));
        }
        if (request.status() != null) {
            applyStatusTransition(task, request.status());
        }
    }

    private void applyStatusTransition(Task task, TaskStatus requestedStatus) {
        switch (requestedStatus) {
            case COMPLETED -> task.complete();
            case BLOCKED -> task.markBlocked();
            case OVERDUE -> task.markOverdue();
            case TODO -> throw new BusinessRuleViolationException(
                    "Cannot set task status back to TODO; no event exists for that transition");
        }
    }

    private void recordEvents(RequestContext context, Task task) {
        for (TaskDomainEvent event : task.pullDomainEvents()) {
            workEventRecorder.record(context, task, event);
        }
    }

    /**
     * Finds a task by id. Unlike {@code ProjectApplicationService}'s equivalent, this does not
     * also check the caller's organization: {@code PATCH}/{@code DELETE /tasks/:id} carry no
     * project id, so the owning project is not known until after this lookup.
     */
    private Task findActiveTask(UUID taskId) {
        return taskRepository.findByIdAndArchivedAtIsNull(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    /**
     * Finds a task by id and verifies its owning project is in the caller's organization.
     * Public so {@code SubtaskApplicationService} and {@code ChecklistApplicationService} can
     * resolve up to the owning project for their own authorization check, the same reuse
     * pattern {@code ProjectApplicationService.findActiveProjectInCallerOrganization} already
     * establishes.
     */
    public Task findActiveTaskInCallerOrganization(RequestContext context, UUID taskId) {
        Task task = findActiveTask(taskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, task.getProjectId());
        return task;
    }
}
