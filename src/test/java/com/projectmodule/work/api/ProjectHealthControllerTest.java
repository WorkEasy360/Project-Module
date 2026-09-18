package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.application.ProjectHealthApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectHealthController.class)
@Import(ApiConfiguration.class)
class ProjectHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectHealthApplicationService projectHealthApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns the project's health indicators, no score field present")
    void returnsHealthIndicators() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectHealthResponse response = new ProjectHealthResponse(2, 3, 4, 1, 0);
        when(projectHealthApplicationService.getHealth(requestContext, projectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/health", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueItemCount").value(2))
                .andExpect(jsonPath("$.unresolvedRiskCount").value(3))
                .andExpect(jsonPath("$.unresolvedIssueCount").value(4))
                .andExpect(jsonPath("$.blockedTaskCount").value(1))
                .andExpect(jsonPath("$.brokenDependencyCount").value(0))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectHealthApplicationService.getHealth(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/health", projectId))
                .andExpect(status().isForbidden());
    }
}
