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
import com.projectmodule.work.application.IssueApplicationService;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.IssueSortField;
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

@WebMvcTest(IssueController.class)
@Import({ApiConfiguration.class, IssueMapper.class})
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueApplicationService issueApplicationService;

    @MockitoBean
    private BulkActionsApplicationService bulkActionsApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static Issue sampleIssue(UUID projectId) {
        return Issue.create(projectId, "Payment gateway down", ProjectPriority.CRITICAL);
    }

    @Test
    @DisplayName("POST creates an issue and returns 201 with a Location header")
    void createsIssue() throws Exception {
        UUID projectId = UUID.randomUUID();
        Issue created = sampleIssue(projectId);
        when(issueApplicationService.createIssue(eq(requestContext), eq(projectId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payment gateway down\",\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/issues/" + created.getId())))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("POST with a blank name is rejected")
    void rejectsInvalidCreateRequest() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with no priority is rejected")
    void rejectsMissingPriority() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payment gateway down\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lists issues for the project, with no filters/sort supplied, exactly as before Filters/List existed")
    void listsIssues() throws Exception {
        UUID projectId = UUID.randomUUID();
        Issue issue = sampleIssue(projectId);
        when(issueApplicationService.listIssues(requestContext, projectId, null, false, null, null))
                .thenReturn(List.of(issue));

        mockMvc.perform(get("/api/v1/projects/{projectId}/issues", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Payment gateway down"));
    }

    /** Coverage for {@code docs/project/14-FILTERS-SPEC.md}. */
    @Test
    @DisplayName("GET binds priority and archived query parameters")
    void listsIssuesWithFilters() throws Exception {
        UUID projectId = UUID.randomUUID();
        Issue issue = sampleIssue(projectId);
        when(issueApplicationService.listIssues(requestContext, projectId, ProjectPriority.CRITICAL, true, null, null))
                .thenReturn(List.of(issue));

        mockMvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .param("priority", "CRITICAL")
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Payment gateway down"));
    }

    @Test
    @DisplayName("GET with an invalid priority filter value returns the standard validation-failed error")
    void invalidPriorityFilterReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .param("priority", "NOT_A_PRIORITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }

    /** Coverage for {@code docs/project/15-LIST-SPEC.md}. */
    @Test
    @DisplayName("GET binds sortBy and sortDir query parameters")
    void listsIssuesWithSort() throws Exception {
        UUID projectId = UUID.randomUUID();
        Issue issue = sampleIssue(projectId);
        when(issueApplicationService.listIssues(requestContext, projectId, null, false,
                        IssueSortField.NAME, SortDirection.DESC))
                .thenReturn(List.of(issue));

        mockMvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .param("sortBy", "NAME")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Payment gateway down"));
    }

    @Test
    @DisplayName("PATCH updates an issue")
    void updatesIssue() throws Exception {
        UUID projectId = UUID.randomUUID();
        Issue issue = sampleIssue(projectId);
        when(issueApplicationService.updateIssue(eq(requestContext), eq(issue.getId()), any()))
                .thenReturn(issue);

        mockMvc.perform(patch("/api/v1/issues/{id}", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE archives the issue and returns 204")
    void archivesIssue() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/issues/{id}", id))
                .andExpect(status().isNoContent());

        verify(issueApplicationService).archiveIssue(requestContext, id);
    }

    /** Coverage for {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @Test
    @DisplayName("POST bulk-archive reports one result per id")
    void bulkArchivesIssues() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(bulkActionsApplicationService.bulkArchiveIssues(eq(requestContext), any()))
                .thenReturn(List.of(new BulkArchiveResultResponse(id, true, null)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/issues/bulk-archive", projectId)
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

        mockMvc.perform(post("/api/v1/projects/{projectId}/issues/bulk-archive", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
    }
}
