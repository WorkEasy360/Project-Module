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
import com.projectmodule.work.application.ChecklistApplicationService;
import com.projectmodule.work.domain.Checklist;
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

@WebMvcTest(ChecklistController.class)
@Import({ApiConfiguration.class, ChecklistMapper.class})
class ChecklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChecklistApplicationService checklistApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Checklist sampleChecklist(UUID taskId) {
        return Checklist.create(taskId, null, "Add password validation");
    }

    @Test
    @DisplayName("POST creates a checklist line and returns 201 with a Location header")
    void createsChecklist() throws Exception {
        UUID taskId = UUID.randomUUID();
        Checklist created = sampleChecklist(taskId);
        when(checklistApplicationService.createChecklist(eq(requestContext), eq(taskId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/checklists", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Add password validation\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/checklists/" + created.getId())))
                .andExpect(jsonPath("$.checked").value(false));
    }

    @Test
    @DisplayName("POST with blank text is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tasks/{taskId}/checklists", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists checklist lines for the task")
    void listsChecklists() throws Exception {
        UUID taskId = UUID.randomUUID();
        Checklist checklist = sampleChecklist(taskId);
        when(checklistApplicationService.listChecklists(requestContext, taskId)).thenReturn(List.of(checklist));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklists", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Add password validation"));
    }

    @Test
    @DisplayName("PATCH toggles checked")
    void updatesChecklist() throws Exception {
        UUID taskId = UUID.randomUUID();
        Checklist checklist = sampleChecklist(taskId);
        checklist.check();
        when(checklistApplicationService.updateChecklist(eq(requestContext), eq(checklist.getId()), any()))
                .thenReturn(checklist);

        mockMvc.perform(patch("/api/v1/checklists/{id}", checklist.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true));
    }

    @Test
    @DisplayName("DELETE archives the checklist line and returns 204")
    void archivesChecklist() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/checklists/{id}", id))
                .andExpect(status().isNoContent());

        verify(checklistApplicationService).archiveChecklist(requestContext, id);
    }
}
