package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.application.ReportsApplicationService;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.TaskStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportsController.class)
@Import(ApiConfiguration.class)
class ReportsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportsApplicationService reportsApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns the project summary report")
    void returnsSummary() throws Exception {
        UUID projectId = UUID.randomUUID();
        Map<TaskStatus, Long> taskCounts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            taskCounts.put(status, 0L);
        }
        taskCounts.put(TaskStatus.BLOCKED, 2L);
        Map<RiskStatus, Long> riskCounts = new EnumMap<>(RiskStatus.class);
        for (RiskStatus status : RiskStatus.values()) {
            riskCounts.put(status, 0L);
        }
        Map<ProjectPriority, Long> priorityCounts = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority priority : ProjectPriority.values()) {
            priorityCounts.put(priority, 0L);
        }

        ProjectSummaryReportResponse response = new ProjectSummaryReportResponse(
                taskCounts, riskCounts, priorityCounts, priorityCounts, 3L, 1L, 0L);
        when(reportsApplicationService.getSummary(requestContext, projectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/reports/summary", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCountByStatus.BLOCKED").value(2))
                .andExpect(jsonPath("$.decisionCount").value(3))
                .andExpect(jsonPath("$.delayedItemCount").value(1));
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(reportsApplicationService.getSummary(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/reports/summary", projectId))
                .andExpect(status().isForbidden());
    }
}
