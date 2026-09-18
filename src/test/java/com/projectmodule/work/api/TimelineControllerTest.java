package com.projectmodule.work.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.TimelineApplicationService;
import com.projectmodule.work.application.TimelinePhaseGroup;
import com.projectmodule.work.application.TimelineResult;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Task;
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

@WebMvcTest(TimelineController.class)
@Import({ApiConfiguration.class, PhaseMapper.class, MilestoneMapper.class, TaskMapper.class})
class TimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimelineApplicationService timelineApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns phases with nested milestones and a flat task list")
    void returnsPhasesAndTasks() throws Exception {
        UUID projectId = UUID.randomUUID();
        Phase phase = Phase.create(projectId, "Design");
        phase.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        Milestone milestone = Milestone.create(projectId, phase.getId(), "Kickoff", LocalDate.of(2026, 1, 15));
        Task task = Task.create(projectId, "Implement login", LocalDate.of(2026, 2, 1), null);

        TimelineResult result = new TimelineResult(
                List.of(new TimelinePhaseGroup(phase, List.of(milestone))), List.of(task));
        when(timelineApplicationService.getTimeline(requestContext, projectId)).thenReturn(result);

        mockMvc.perform(get("/api/v1/projects/{projectId}/timeline", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phases[0].name").value("Design"))
                .andExpect(jsonPath("$.phases[0].milestones[0].name").value("Kickoff"))
                .andExpect(jsonPath("$.tasks[0].name").value("Implement login"));
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(timelineApplicationService.getTimeline(requestContext, projectId))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/timeline", projectId))
                .andExpect(status().isForbidden());
    }
}
