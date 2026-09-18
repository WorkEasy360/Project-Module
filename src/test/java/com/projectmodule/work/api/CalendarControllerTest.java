package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.api.dto.CalendarEntryResponse;
import com.projectmodule.work.application.CalendarApplicationService;
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

@WebMvcTest(CalendarController.class)
@Import(ApiConfiguration.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarApplicationService calendarApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET with no from/to returns every dated entry")
    void returnsAllEntriesWhenWindowOmitted() throws Exception {
        UUID projectId = UUID.randomUUID();
        CalendarEntryResponse entry = new CalendarEntryResponse(
                "TASK", UUID.randomUUID(), "Implement login", LocalDate.of(2026, 3, 1), null, null);
        when(calendarApplicationService.getCalendar(requestContext, projectId, null, null))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/projects/{projectId}/calendar", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].entityType").value("TASK"))
                .andExpect(jsonPath("$.entries[0].name").value("Implement login"));
    }

    @Test
    @DisplayName("GET binds from and to query parameters")
    void bindsFromAndTo() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(calendarApplicationService.getCalendar(
                        requestContext, projectId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects/{projectId}/calendar", projectId)
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    @DisplayName("GET without VIEW_PROJECT returns the standard 403")
    void deniesWithoutPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(calendarApplicationService.getCalendar(requestContext, projectId, null, null))
                .thenThrow(new AuthorizationException("denied"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/calendar", projectId))
                .andExpect(status().isForbidden());
    }
}
