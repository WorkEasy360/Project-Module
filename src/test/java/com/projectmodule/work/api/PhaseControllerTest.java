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
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.PhaseApplicationService;
import com.projectmodule.work.domain.Phase;
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

@WebMvcTest(PhaseController.class)
@Import({ApiConfiguration.class, PhaseMapper.class})
class PhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhaseApplicationService phaseApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Phase samplePhase(UUID projectId) {
        return Phase.create(projectId, "Discovery");
    }

    @Test
    @DisplayName("POST creates a phase and returns 201 with a Location header")
    void createsPhase() throws Exception {
        UUID projectId = UUID.randomUUID();
        Phase created = samplePhase(projectId);
        when(phaseApplicationService.createPhase(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/phases", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Discovery\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/phases/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Discovery"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/phases", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists phases for the project")
    void listsPhases() throws Exception {
        UUID projectId = UUID.randomUUID();
        Phase phase = samplePhase(projectId);
        when(phaseApplicationService.listPhases(requestContext, projectId)).thenReturn(List.of(phase));

        mockMvc.perform(get("/api/v1/projects/{projectId}/phases", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Discovery"));
    }

    @Test
    @DisplayName("PATCH updates a phase")
    void updatesPhase() throws Exception {
        UUID projectId = UUID.randomUUID();
        Phase phase = samplePhase(projectId);
        when(phaseApplicationService.updatePhase(eq(requestContext), eq(phase.getId()), any()))
                .thenReturn(phase);

        mockMvc.perform(patch("/api/v1/phases/{id}", phase.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH on a missing phase returns the standard not-found error")
    void updateMissingPhaseReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(phaseApplicationService.updatePhase(eq(requestContext), eq(id), any()))
                .thenThrow(new ResourceNotFoundException("Phase", id));

        mockMvc.perform(patch("/api/v1/phases/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"));
    }

    @Test
    @DisplayName("DELETE archives the phase and returns 204")
    void archivesPhase() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/phases/{id}", id))
                .andExpect(status().isNoContent());

        verify(phaseApplicationService).archivePhase(requestContext, id);
    }
}
