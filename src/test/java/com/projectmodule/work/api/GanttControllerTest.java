package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.GanttBarResponse;
import com.projectmodule.work.api.dto.GanttPointResponse;
import com.projectmodule.work.api.dto.GanttResponse;
import com.projectmodule.work.application.GanttApplicationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GanttController.class)
@Import(ApiConfiguration.class)
class GanttControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GanttApplicationService ganttApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns phases as bars, tasks/milestones as points, and dependencies")
    void returnsGantt() throws Exception {
        UUID projectId = UUID.randomUUID();
        GanttBarResponse bar = new GanttBarResponse(
                UUID.randomUUID(), "Design", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        GanttPointResponse taskPoint = new GanttPointResponse(
                UUID.randomUUID(), "Implement login", LocalDate.of(2026, 3, 1));
        GanttResponse response = new GanttResponse(List.of(bar), List.of(taskPoint), List.of(), List.of());
        when(ganttApplicationService.getGantt(requestContext, projectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}/gantt", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phases[0].name").value("Design"))
                .andExpect(jsonPath("$.phases[0].startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.tasks[0].name").value("Implement login"))
                .andExpect(jsonPath("$.tasks[0].date").value("2026-03-01"));
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(ganttApplicationService.getGantt(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/gantt", projectId))
                .andExpect(status().isForbidden());
    }
}
