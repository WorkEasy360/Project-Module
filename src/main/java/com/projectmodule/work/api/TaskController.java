package com.projectmodule.work.api;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.BulkArchiveRequest;
import com.projectmodule.work.api.dto.BulkArchiveResponse;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.KanbanBoardResponse;
import com.projectmodule.work.api.dto.KanbanColumnResponse;
import com.projectmodule.work.api.dto.TaskResponse;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskSortField;
import com.projectmodule.work.domain.TaskStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for tasks, exactly as {@code docs/project/04-API.md} defines them — including
 * the one single-item {@code GET /tasks/{id}}, unlike Phase/Milestone/Task List, which have
 * none. Status transitions and assignment go through {@code PATCH} — see
 * {@link com.projectmodule.work.api.dto.UpdateTaskRequest}. Holds no business logic or
 * authorization decision; both are delegated to {@link TaskApplicationService}.
 */
@RestController
public class TaskController {

    private final TaskApplicationService taskApplicationService;
    private final BulkActionsApplicationService bulkActionsApplicationService;
    private final TaskMapper taskMapper;
    private final RequestContext requestContext;

    public TaskController(TaskApplicationService taskApplicationService,
                          BulkActionsApplicationService bulkActionsApplicationService,
                          TaskMapper taskMapper,
                          RequestContext requestContext) {
        this.taskApplicationService = taskApplicationService;
        this.bulkActionsApplicationService = bulkActionsApplicationService;
        this.taskMapper = taskMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> createTask(@PathVariable UUID projectId,
                                                    @Valid @RequestBody CreateTaskRequest request) {
        Task created = taskApplicationService.createTask(requestContext, projectId, request);
        TaskResponse response = taskMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/tasks/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * All filter and sort parameters are optional; a request with none behaves exactly as before
     * these features existed. See {@code docs/project/14-FILTERS-SPEC.md} and
     * {@code docs/project/15-LIST-SPEC.md}.
     */
    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskResponse> listTasks(@PathVariable UUID projectId,
                                        @RequestParam(required = false) TaskStatus status,
                                        @RequestParam(required = false) UUID assigneeId,
                                        @RequestParam(required = false) LocalDate dueDateBefore,
                                        @RequestParam(required = false) LocalDate dueDateAfter,
                                        @RequestParam(defaultValue = "false") boolean archived,
                                        @RequestParam(required = false) TaskSortField sortBy,
                                        @RequestParam(required = false) SortDirection sortDir) {
        return taskApplicationService.listTasks(requestContext, projectId, status, assigneeId,
                        dueDateBefore, dueDateAfter, archived, sortBy, sortDir).stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    /** The project's Kanban board. See {@code docs/project/16-KANBAN-SPEC.md}. */
    @GetMapping("/projects/{projectId}/tasks/board")
    public KanbanBoardResponse getBoard(@PathVariable UUID projectId) {
        Map<TaskStatus, List<Task>> board = taskApplicationService.getBoard(requestContext, projectId);
        List<KanbanColumnResponse> columns = new ArrayList<>();
        for (TaskStatus status : TaskStatus.values()) {
            List<TaskResponse> tasks = board.get(status).stream().map(taskMapper::toResponse).toList();
            columns.add(new KanbanColumnResponse(status, tasks));
        }
        return new KanbanBoardResponse(columns);
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse getTask(@PathVariable UUID id) {
        return taskMapper.toResponse(taskApplicationService.getTask(requestContext, id));
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse updateTask(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        Task updated = taskApplicationService.updateTask(requestContext, id, request);
        return taskMapper.toResponse(updated);
    }

    /** Soft-archives the task. See {@link Task#archive}. */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> archiveTask(@PathVariable UUID id) {
        taskApplicationService.archiveTask(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-archives tasks. See {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @PostMapping("/projects/{projectId}/tasks/bulk-archive")
    public BulkArchiveResponse bulkArchiveTasks(@PathVariable UUID projectId,
                                                @Valid @RequestBody BulkArchiveRequest request) {
        return new BulkArchiveResponse(bulkActionsApplicationService.bulkArchiveTasks(requestContext, request.ids()));
    }
}
