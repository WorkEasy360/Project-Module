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
import com.projectmodule.work.api.dto.BulkArchiveResultResponse;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.DecisionApplicationService;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.DecisionSortField;
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

@WebMvcTest(DecisionController.class)
@Import({ApiConfiguration.class, DecisionMapper.class})
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DecisionApplicationService decisionApplicationService;

    @MockitoBean
    private BulkActionsApplicationService bulkActionsApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Decision sampleDecision(UUID projectId) {
        return Decision.create(projectId, "Use PostgreSQL for persistence");
    }

    @Test
    @DisplayName("POST creates a decision and returns 201 with a Location header")
    void createsDecision() throws Exception {
        UUID projectId = UUID.randomUUID();
        Decision created = sampleDecision(projectId);
        when(decisionApplicationService.createDecision(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Use PostgreSQL for persistence\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/decisions/" + created.getId())))
                .andExpect(jsonPath("$.name").value("Use PostgreSQL for persistence"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists decisions for the project, with no filters/sort supplied, exactly as before Filters/List existed")
    void listsDecisions() throws Exception {
        UUID projectId = UUID.randomUUID();
        Decision decision = sampleDecision(projectId);
        when(decisionApplicationService.listDecisions(requestContext, projectId, null, false, null, null))
                .thenReturn(List.of(decision));

        mockMvc.perform(get("/api/v1/projects/{projectId}/decisions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Use PostgreSQL for persistence"));
    }

    /** Coverage for {@code docs/project/14-FILTERS-SPEC.md}. */
    @Test
    @DisplayName("GET binds decidedBy and archived query parameters")
    void listsDecisionsWithFilters() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID deciderId = UUID.randomUUID();
        Decision decision = sampleDecision(projectId);
        when(decisionApplicationService.listDecisions(requestContext, projectId, deciderId, true, null, null))
                .thenReturn(List.of(decision));

        mockMvc.perform(get("/api/v1/projects/{projectId}/decisions", projectId)
                        .param("decidedBy", deciderId.toString())
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Use PostgreSQL for persistence"));
    }

    @Test
    @DisplayName("GET with an invalid decidedBy filter value returns the standard validation-failed error")
    void invalidDecidedByFilterReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects/{projectId}/decisions", projectId)
                        .param("decidedBy", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    /** Coverage for {@code docs/project/15-LIST-SPEC.md}. */
    @Test
    @DisplayName("GET binds sortBy and sortDir query parameters")
    void listsDecisionsWithSort() throws Exception {
        UUID projectId = UUID.randomUUID();
        Decision decision = sampleDecision(projectId);
        when(decisionApplicationService.listDecisions(requestContext, projectId, null, false,
                        DecisionSortField.NAME, SortDirection.DESC))
                .thenReturn(List.of(decision));

        mockMvc.perform(get("/api/v1/projects/{projectId}/decisions", projectId)
                        .param("sortBy", "NAME")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Use PostgreSQL for persistence"));
    }

    @Test
    @DisplayName("PATCH updates a decision")
    void updatesDecision() throws Exception {
        UUID projectId = UUID.randomUUID();
        Decision decision = sampleDecision(projectId);
        when(decisionApplicationService.updateDecision(eq(requestContext), eq(decision.getId()), any()))
                .thenReturn(decision);

        mockMvc.perform(patch("/api/v1/decisions/{id}", decision.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the decision and returns 204")
    void archivesDecision() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/decisions/{id}", id))
                .andExpect(status().isNoContent());

        verify(decisionApplicationService).archiveDecision(requestContext, id);
    }

    /** Coverage for {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @Test
    @DisplayName("POST bulk-archive reports one result per id")
    void bulkArchivesDecisions() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(bulkActionsApplicationService.bulkArchiveDecisions(eq(requestContext), any()))
                .thenReturn(List.of(new BulkArchiveResultResponse(id, true, null)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions/bulk-archive", projectId)
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

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }
}
