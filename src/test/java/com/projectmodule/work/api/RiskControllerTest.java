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
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.BulkArchiveResultResponse;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.RiskApplicationService;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskSortField;
import com.projectmodule.work.domain.RiskStatus;
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

@WebMvcTest(RiskController.class)
@Import({ApiConfiguration.class, RiskMapper.class})
class RiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskApplicationService riskApplicationService;

    @MockitoBean
    private BulkActionsApplicationService bulkActionsApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Risk sampleRisk(UUID projectId) {
        return Risk.create(projectId, "Vendor delay", ProjectPriority.HIGH);
    }

    @Test
    @DisplayName("POST creates a risk and returns 201 with a Location header")
    void createsRisk() throws Exception {
        UUID projectId = UUID.randomUUID();
        Risk created = sampleRisk(projectId);
        when(riskApplicationService.createRisk(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/risks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vendor delay\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/risks/" + created.getId())))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/risks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"priority\":\"HIGH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with no priority is rejected")
    void rejectsMissingPriority() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/risks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vendor delay\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists risks for the project, with no filters/sort supplied, exactly as before Filters/List existed")
    void listsRisks() throws Exception {
        UUID projectId = UUID.randomUUID();
        Risk risk = sampleRisk(projectId);
        when(riskApplicationService.listRisks(requestContext, projectId, null, null, false, null, null))
                .thenReturn(List.of(risk));

        mockMvc.perform(get("/api/v1/projects/{projectId}/risks", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Vendor delay"));
    }

    /** Coverage for {@code docs/project/14-FILTERS-SPEC.md}. */
    @Test
    @DisplayName("GET binds status, priority and archived query parameters")
    void listsRisksWithFilters() throws Exception {
        UUID projectId = UUID.randomUUID();
        Risk risk = sampleRisk(projectId);
        when(riskApplicationService.listRisks(
                        requestContext, projectId, RiskStatus.OPEN, ProjectPriority.HIGH, true, null, null))
                .thenReturn(List.of(risk));

        mockMvc.perform(get("/api/v1/projects/{projectId}/risks", projectId)
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Vendor delay"));
    }

    @Test
    @DisplayName("GET with an invalid priority filter value returns the standard validation-failed error")
    void invalidPriorityFilterReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects/{projectId}/risks", projectId)
                        .param("priority", "NOT_A_PRIORITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    /** Coverage for {@code docs/project/15-LIST-SPEC.md}. */
    @Test
    @DisplayName("GET binds sortBy and sortDir query parameters")
    void listsRisksWithSort() throws Exception {
        UUID projectId = UUID.randomUUID();
        Risk risk = sampleRisk(projectId);
        when(riskApplicationService.listRisks(requestContext, projectId, null, null, false,
                        RiskSortField.PRIORITY, SortDirection.DESC))
                .thenReturn(List.of(risk));

        mockMvc.perform(get("/api/v1/projects/{projectId}/risks", projectId)
                        .param("sortBy", "PRIORITY")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Vendor delay"));
    }

    @Test
    @DisplayName("PATCH resolves a risk")
    void updatesRisk() throws Exception {
        UUID projectId = UUID.randomUUID();
        Risk risk = sampleRisk(projectId);
        risk.resolve();
        when(riskApplicationService.updateRisk(eq(requestContext), eq(risk.getId()), any()))
                .thenReturn(risk);

        mockMvc.perform(patch("/api/v1/risks/{id}", risk.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("DELETE archives the risk and returns 204")
    void archivesRisk() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/risks/{id}", id))
                .andExpect(status().isNoContent());

        verify(riskApplicationService).archiveRisk(requestContext, id);
    }

    /** Coverage for {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @Test
    @DisplayName("POST bulk-archive reports one result per id")
    void bulkArchivesRisks() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(bulkActionsApplicationService.bulkArchiveRisks(eq(requestContext), any()))
                .thenReturn(List.of(new BulkArchiveResultResponse(id, true, null)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/risks/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(id.toString()))
                .andExpect(jsonPath("$.results[0].succeeded").value(true));
    }

    @Test
    @DisplayName("POST bulk-archive with an empty ids list returns the standard validation-failed error")
    void bulkArchiveWithEmptyIdsReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/risks/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }
}
