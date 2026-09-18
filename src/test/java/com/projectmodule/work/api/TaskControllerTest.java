package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.BulkArchiveResultResponse;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskSortField;
import com.projectmodule.work.domain.TaskStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import({ApiConfiguration.class, TaskMapper.class})
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskApplicationService taskApplicationService;

    @MockitoBean
    private BulkActionsApplicationService bulkActionsApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Task sampleTask(UUID projectId) {
        return Task.create(projectId, "Implement login", LocalDate.of(2026, 3, 1), null);
    }

    @Test
    @DisplayName("POST creates a task and returns 201 with a Location header")
    void createsTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task created = sampleTask(projectId);
        when(taskApplicationService.createTask(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Implement login\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/tasks/" + created.getId())))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists tasks for the project, with no filters/sort supplied, exactly as before Filters/List existed")
    void listsTasks() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task task = sampleTask(projectId);
        when(taskApplicationService.listTasks(requestContext, projectId, null, null, null, null, false, null, null))
                .thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Implement login"));
    }

    /** Coverage for {@code docs/project/14-FILTERS-SPEC.md}. */
    @Test
    @DisplayName("GET binds status, assigneeId, dueDateBefore, dueDateAfter and archived query parameters")
    void listsTasksWithFilters() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = sampleTask(projectId);
        when(taskApplicationService.listTasks(requestContext, projectId,
                        TaskStatus.BLOCKED, assigneeId,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), true, null, null))
                .thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", projectId)
                        .param("status", "BLOCKED")
                        .param("assigneeId", assigneeId.toString())
                        .param("dueDateBefore", "2026-06-01")
                        .param("dueDateAfter", "2026-01-01")
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Implement login"));
    }

    @Test
    @DisplayName("GET with an invalid status filter value returns the standard validation-failed error")
    void invalidStatusFilterReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", projectId)
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    /** Coverage for {@code docs/project/15-LIST-SPEC.md}. */
    @Test
    @DisplayName("GET binds sortBy and sortDir query parameters")
    void listsTasksWithSort() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task task = sampleTask(projectId);
        when(taskApplicationService.listTasks(requestContext, projectId, null, null, null, null, false,
                        TaskSortField.DUE_DATE, SortDirection.DESC))
                .thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", projectId)
                        .param("sortBy", "DUE_DATE")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Implement login"));
    }

    @Test
    @DisplayName("GET with an invalid sortBy value returns the standard validation-failed error")
    void invalidSortByReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", projectId)
                        .param("sortBy", "NOT_A_FIELD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    /** Coverage for {@code docs/project/16-KANBAN-SPEC.md}. */
    @Test
    @DisplayName("GET board groups tasks by status with every column present")
    void getsBoard() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task todo = sampleTask(projectId);
        Map<TaskStatus, List<Task>> board = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            board.put(status, List.of());
        }
        board.put(TaskStatus.TODO, List.of(todo));
        when(taskApplicationService.getBoard(requestContext, projectId)).thenReturn(board);

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/board", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.columns[0].status").value("TODO"))
                .andExpect(jsonPath("$.columns[0].tasks[0].name").value("Implement login"))
                .andExpect(jsonPath("$.columns[1].status").value("BLOCKED"))
                .andExpect(jsonPath("$.columns[1].tasks", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("GET by id returns the task")
    void getsTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task task = sampleTask(projectId);
        when(taskApplicationService.getTask(requestContext, task.getId())).thenReturn(task);

        mockMvc.perform(get("/api/v1/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Implement login"));
    }

    @Test
    @DisplayName("GET by id on a missing task returns the standard not-found error")
    void getMissingTaskReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskApplicationService.getTask(requestContext, id))
                .thenThrow(new ResourceNotFoundException("Task", id));

        mockMvc.perform(get("/api/v1/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"));
    }

    @Test
    @DisplayName("PATCH updates a task")
    void updatesTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        Task task = sampleTask(projectId);
        when(taskApplicationService.updateTask(eq(requestContext), eq(task.getId()), any()))
                .thenReturn(task);

        mockMvc.perform(patch("/api/v1/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the task and returns 204")
    void archivesTask() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/tasks/{id}", id))
                .andExpect(status().isNoContent());

        verify(taskApplicationService).archiveTask(requestContext, id);
    }

    /** Coverage for {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @Test
    @DisplayName("POST bulk-archive reports one result per id, including a partial failure")
    void bulkArchivesTasks() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID succeeded = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        when(bulkActionsApplicationService.bulkArchiveTasks(eq(requestContext), any()))
                .thenReturn(List.of(
                        new BulkArchiveResultResponse(succeeded, true, null),
                        new BulkArchiveResultResponse(failed, false, "Task not found")));

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + succeeded + "\",\"" + failed + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(succeeded.toString()))
                .andExpect(jsonPath("$.results[0].succeeded").value(true))
                .andExpect(jsonPath("$.results[1].id").value(failed.toString()))
                .andExpect(jsonPath("$.results[1].succeeded").value(false))
                .andExpect(jsonPath("$.results[1].error").value("Task not found"));
    }

    @Test
    @DisplayName("POST bulk-archive with an empty ids list returns the standard validation-failed error")
    void bulkArchiveWithEmptyIdsReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }
}
