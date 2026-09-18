package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.SearchResultResponse;
import com.projectmodule.work.application.SearchApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import(ApiConfiguration.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchApplicationService searchApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns a page of search results")
    void returnsSearchResults() throws Exception {
        UUID projectId = UUID.randomUUID();
        SearchResultResponse result = new SearchResultResponse(
                "TASK", UUID.randomUUID(), projectId, "Fix login bug", "Users cannot log in",
                false, Instant.now(), Instant.now());
        Pageable pageable = PageRequest.of(0, 20);
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq("login"), any()))
                .thenReturn(new PageImpl<>(List.of(result), pageable, 1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId).param("q", "login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resourceType").value("TASK"))
                .andExpect(jsonPath("$.content[0].name").value("Fix login bug"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET with a blank q returns the standard 422 business-rule error")
    void blankQueryReturns422() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq(""), any()))
                .thenThrow(new BusinessRuleViolationException("Search query must not be blank"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId).param("q", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/business-rule-violation"));
    }

    @Test
    @DisplayName("GET with no q at all also returns 422, not a generic missing-parameter 400")
    void missingQueryReturns422() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq((String) null), any()))
                .thenThrow(new BusinessRuleViolationException("Search query must not be blank"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq("term"), any()))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId).param("q", "term"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET against a project outside the caller's organization returns the standard 404")
    void crossOrganizationReturns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq("term"), any()))
                .thenThrow(new ResourceNotFoundException("Project", projectId));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId).param("q", "term"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET honors page/size query parameters")
    void honorsPagination() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(searchApplicationService.search(eq(requestContext), eq(projectId), eq("term"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/v1/projects/{projectId}/search", projectId)
                        .param("q", "term")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }
}
