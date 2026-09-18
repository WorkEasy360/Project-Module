package com.projectmodule.automation.api;

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

import com.projectmodule.automation.application.ProjectAutomationApplicationService;
import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.config.ApiConfiguration;
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

@WebMvcTest(ProjectAutomationController.class)
@Import({ApiConfiguration.class, ProjectAutomationMapper.class})
class ProjectAutomationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectAutomationApplicationService projectAutomationApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static ProjectAutomation sample(UUID projectId) {
        return ProjectAutomation.create(projectId, "Notify on overdue", "task.overdue",
                AutomationActionType.NOTIFY, UUID.randomUUID(), null, "A task is overdue");
    }

    @Test
    @DisplayName("POST creates an automation and returns 201 with a Location header")
    void createsAutomation() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectAutomation created = sample(projectId);
        when(projectAutomationApplicationService.createAutomation(eq(requestContext), eq(projectId),
                        any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/automations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notify on overdue\",\"triggerEvent\":\"task.overdue\","
                                + "\"actionType\":\"NOTIFY\",\"actionRecipientId\":\"" + UUID.randomUUID()
                                + "\",\"actionMessage\":\"A task is overdue\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/automations/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Notify on overdue"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsBlankName() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/automations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"triggerEvent\":\"task.overdue\",\"actionType\":\"NOTIFY\","
                                + "\"actionRecipientId\":\"" + UUID.randomUUID()
                                + "\",\"actionMessage\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Coverage for docs/project/28-AUTOMATION-SPEC.md §2: only catalogued event types are valid triggers. */
    @Test
    @DisplayName("POST with a triggerEvent outside the catalogue returns the standard validation-failed error")
    void rejectsUncatalogedTriggerEvent() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectAutomationApplicationService.createAutomation(eq(requestContext), eq(projectId),
                        any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ValidationException(
                        "triggerEvent must be one of the catalogued event types; was 'task.deleted'"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/automations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"triggerEvent\":\"task.deleted\",\"actionType\":\"NOTIFY\","
                                + "\"actionRecipientId\":\"" + UUID.randomUUID()
                                + "\",\"actionMessage\":\"m\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    @Test
    @DisplayName("GET lists automations for the project")
    void listsAutomations() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectAutomation automation = sample(projectId);
        when(projectAutomationApplicationService.listAutomations(requestContext, projectId))
                .thenReturn(List.of(automation));

        mockMvc.perform(get("/api/v1/projects/{projectId}/automations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Notify on overdue"));
    }

    @Test
    @DisplayName("PATCH updates an automation")
    void updatesAutomation() throws Exception {
        ProjectAutomation automation = sample(UUID.randomUUID());
        when(projectAutomationApplicationService.updateAutomation(
                        eq(requestContext), eq(automation.getId()), any(), any(), any(), any(), eq(0L)))
                .thenReturn(automation);

        mockMvc.perform(patch("/api/v1/automations/{id}", automation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the automation and returns 204")
    void archivesAutomation() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/automations/{id}", id))
                .andExpect(status().isNoContent());

        verify(projectAutomationApplicationService).archiveAutomation(requestContext, id);
    }

    @Test
    @DisplayName("GET runs returns execution history")
    void listsRuns() throws Exception {
        UUID automationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        AutomationRun run = AutomationRun.succeeded(automationId, projectId, "task.overdue");
        when(projectAutomationApplicationService.listRuns(requestContext, automationId)).thenReturn(List.of(run));

        mockMvc.perform(get("/api/v1/automations/{id}/runs", automationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].triggerEvent").value("task.overdue"));
    }
}
