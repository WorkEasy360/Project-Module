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

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.TaskListApplicationService;
import com.projectmodule.work.domain.TaskList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskListController.class)
@Import({ApiConfiguration.class, TaskListMapper.class})
class TaskListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskListApplicationService taskListApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static TaskList sampleTaskList(UUID projectId) {
        return TaskList.create(projectId, null, "Backend");
    }

    @Test
    @DisplayName("POST creates a task list and returns 201 with a Location header")
    void createsTaskList() throws Exception {
        UUID projectId = UUID.randomUUID();
        TaskList created = sampleTaskList(projectId);
        when(taskListApplicationService.createTaskList(eq(requestContext), eq(projectId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/task-lists", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/task-lists/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Backend"));
    }

    @Test
    @DisplayName("GET lists task lists for the project")
    void listsTaskLists() throws Exception {
        UUID projectId = UUID.randomUUID();
        TaskList taskList = sampleTaskList(projectId);
        when(taskListApplicationService.listTaskLists(requestContext, projectId))
                .thenReturn(List.of(taskList));

        mockMvc.perform(get("/api/v1/projects/{projectId}/task-lists", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Backend"));
    }

    @Test
    @DisplayName("PATCH updates a task list")
    void updatesTaskList() throws Exception {
        UUID projectId = UUID.randomUUID();
        TaskList taskList = sampleTaskList(projectId);
        when(taskListApplicationService.updateTaskList(eq(requestContext), eq(taskList.getId()), any()))
                .thenReturn(taskList);

        mockMvc.perform(patch("/api/v1/task-lists/{id}", taskList.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the task list and returns 204")
    void archivesTaskList() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/task-lists/{id}", id))
                .andExpect(status().isNoContent());

        verify(taskListApplicationService).archiveTaskList(requestContext, id);
    }
}
