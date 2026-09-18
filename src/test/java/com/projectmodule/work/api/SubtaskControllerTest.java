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
import com.projectmodule.work.application.SubtaskApplicationService;
import com.projectmodule.work.domain.Subtask;
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

@WebMvcTest(SubtaskController.class)
@Import({ApiConfiguration.class, SubtaskMapper.class})
class SubtaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubtaskApplicationService subtaskApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Subtask sampleSubtask(UUID taskId) {
        return Subtask.create(taskId, "Write unit tests");
    }

    @Test
    @DisplayName("POST creates a subtask and returns 201 with a Location header")
    void createsSubtask() throws Exception {
        UUID taskId = UUID.randomUUID();
        Subtask created = sampleSubtask(taskId);
        when(subtaskApplicationService.createSubtask(eq(requestContext), eq(taskId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/subtasks", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Write unit tests\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/subtasks/" + created.getId())))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    @DisplayName("GET lists subtasks for the task")
    void listsSubtasks() throws Exception {
        UUID taskId = UUID.randomUUID();
        Subtask subtask = sampleSubtask(taskId);
        when(subtaskApplicationService.listSubtasks(requestContext, taskId)).thenReturn(List.of(subtask));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/subtasks", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Write unit tests"));
    }

    @Test
    @DisplayName("PATCH toggles completion")
    void updatesSubtask() throws Exception {
        UUID taskId = UUID.randomUUID();
        Subtask subtask = sampleSubtask(taskId);
        subtask.complete();
        when(subtaskApplicationService.updateSubtask(eq(requestContext), eq(subtask.getId()), any()))
                .thenReturn(subtask);

        mockMvc.perform(patch("/api/v1/subtasks/{id}", subtask.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("DELETE archives the subtask and returns 204")
    void archivesSubtask() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/subtasks/{id}", id))
                .andExpect(status().isNoContent());

        verify(subtaskApplicationService).archiveSubtask(requestContext, id);
    }
}
