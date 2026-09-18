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
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.MilestoneApplicationService;
import com.projectmodule.work.domain.Milestone;
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

@WebMvcTest(MilestoneController.class)
@Import({ApiConfiguration.class, MilestoneMapper.class})
class MilestoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MilestoneApplicationService milestoneApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Milestone sampleMilestone(UUID projectId) {
        return Milestone.create(projectId, null, "Beta launch", null);
    }

    @Test
    @DisplayName("POST creates a milestone and returns 201 with a Location header")
    void createsMilestone() throws Exception {
        UUID projectId = UUID.randomUUID();
        Milestone created = sampleMilestone(projectId);
        when(milestoneApplicationService.createMilestone(eq(requestContext), eq(projectId), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/milestones", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta launch\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/milestones/" + created.getId())))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET lists milestones for the project")
    void listsMilestones() throws Exception {
        UUID projectId = UUID.randomUUID();
        Milestone milestone = sampleMilestone(projectId);
        when(milestoneApplicationService.listMilestones(requestContext, projectId))
                .thenReturn(List.of(milestone));

        mockMvc.perform(get("/api/v1/projects/{projectId}/milestones", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Beta launch"));
    }

    @Test
    @DisplayName("PATCH with status=COMPLETED updates the milestone")
    void completesMilestoneViaPatch() throws Exception {
        UUID projectId = UUID.randomUUID();
        Milestone milestone = sampleMilestone(projectId);
        milestone.complete();
        when(milestoneApplicationService.updateMilestone(eq(requestContext), eq(milestone.getId()), any()))
                .thenReturn(milestone);

        mockMvc.perform(patch("/api/v1/milestones/{id}", milestone.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH with status=PENDING returns the standard business-rule error")
    void rejectsPendingTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(milestoneApplicationService.updateMilestone(eq(requestContext), eq(id), any()))
                .thenThrow(new BusinessRuleViolationException("not supported"));

        mockMvc.perform(patch("/api/v1/milestones/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"version\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DELETE archives the milestone and returns 204")
    void archivesMilestone() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/milestones/{id}", id))
                .andExpect(status().isNoContent());

        verify(milestoneApplicationService).archiveMilestone(requestContext, id);
    }
}
