package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreateTaskListRequest;
import com.projectmodule.work.api.dto.TaskListResponse;
import com.projectmodule.work.api.dto.UpdateTaskListRequest;
import com.projectmodule.work.application.TaskListApplicationService;
import com.projectmodule.work.domain.TaskList;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for task lists.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b>, unlike Phases and Milestones — this
 * extends their exact documented URL pattern (collection nested under a project, item flat)
 * rather than inventing a different shape. Holds no business logic or authorization decision;
 * both are delegated to {@link TaskListApplicationService}.
 */
@RestController
public class TaskListController {

    private final TaskListApplicationService taskListApplicationService;
    private final TaskListMapper taskListMapper;
    private final RequestContext requestContext;

    public TaskListController(TaskListApplicationService taskListApplicationService,
                              TaskListMapper taskListMapper,
                              RequestContext requestContext) {
        this.taskListApplicationService = taskListApplicationService;
        this.taskListMapper = taskListMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/task-lists")
    public ResponseEntity<TaskListResponse> createTaskList(@PathVariable UUID projectId,
                                                            @Valid @RequestBody CreateTaskListRequest request) {
        TaskList created = taskListApplicationService.createTaskList(requestContext, projectId, request);
        TaskListResponse response = taskListMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/task-lists/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/task-lists")
    public List<TaskListResponse> listTaskLists(@PathVariable UUID projectId) {
        return taskListApplicationService.listTaskLists(requestContext, projectId).stream()
                .map(taskListMapper::toResponse)
                .toList();
    }

    @PatchMapping("/task-lists/{id}")
    public TaskListResponse updateTaskList(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateTaskListRequest request) {
        TaskList updated = taskListApplicationService.updateTaskList(requestContext, id, request);
        return taskListMapper.toResponse(updated);
    }

    /** Soft-archives the task list. See {@link TaskList#archive}. */
    @DeleteMapping("/task-lists/{id}")
    public ResponseEntity<Void> archiveTaskList(@PathVariable UUID id) {
        taskListApplicationService.archiveTaskList(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
