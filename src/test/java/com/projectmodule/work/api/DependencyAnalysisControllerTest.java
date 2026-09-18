package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.application.DependencyAnalysisApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DependencyAnalysisController.class)
@Import(ApiConfiguration.class)
class DependencyAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DependencyAnalysisApplicationService dependencyAnalysisApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns cycles, blocked task ids and broken dependencies")
    void returnsAnalysis() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        DependencyAnalysisResponse response = new DependencyAnalysisResponse(
                List.of(List.of(taskId)), List.of(taskId), List.of());
        when(dependencyAnalysisApplicationService.getAnalysis(requestContext, projectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/dependencies/analysis", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycles[0][0]").value(taskId.toString()))
                .andExpect(jsonPath("$.blockedTaskIds[0]").value(taskId.toString()));
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(dependencyAnalysisApplicationService.getAnalysis(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/dependencies/analysis", projectId))
                .andExpect(status().isForbidden());
    }
}
