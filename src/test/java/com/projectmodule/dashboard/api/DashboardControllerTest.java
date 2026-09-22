package com.projectmodule.dashboard.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.api.GlobalExceptionHandler;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.dashboard.api.dto.DashboardResponse;
import com.projectmodule.dashboard.application.DashboardApplicationService;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller slice test: routing and serialization. Aggregation logic is covered by
 * {@code DashboardApplicationServiceTest}.
 */
@WebMvcTest(DashboardController.class)
@Import({ApiConfiguration.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardApplicationService dashboardApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("GET returns the dashboard summary")
    void getsDashboard() throws Exception {
        Map<ProjectStatus, Long> byStatus = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            byStatus.put(status, 0L);
        }
        byStatus.put(ProjectStatus.ACTIVE, 3L);

        Map<ProjectPriority, Long> byPriority = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority priority : ProjectPriority.values()) {
            byPriority.put(priority, 0L);
        }
        byPriority.put(ProjectPriority.HIGH, 2L);

        when(dashboardApplicationService.getDashboard(requestContext))
                .thenReturn(new DashboardResponse(5L, 1L, byStatus, byPriority));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProjectCount").value(5))
                .andExpect(jsonPath("$.archivedProjectCount").value(1))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(3))
                .andExpect(jsonPath("$.byStatus.PLANNING").value(0))
                .andExpect(jsonPath("$.byPriority.HIGH").value(2))
                .andExpect(jsonPath("$.byPriority.LOW").value(0));
    }

    /**
     * The identity contract, end to end through the web layer.
     *
     * <p>A caller that supplies no organization must be refused. Both halves of this were already
     * covered separately — {@code DashboardApplicationServiceTest} proves the service raises
     * {@link MissingIdentityException} when the context carries no organization, and
     * {@code GlobalExceptionHandlerTest} proves that exception maps to 401 {@code identity-missing}
     * — but nothing proved they compose into a 401 at the endpoint. This pins that down, so a
     * future change to the advice, the filter order or the controller cannot silently turn an
     * unidentified request into a successful one.
     */
    @Test
    @DisplayName("GET without an organization in context is refused with 401 identity-missing")
    void refusesRequestWithoutIdentity() throws Exception {
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenThrow(new MissingIdentityException("No organization was supplied with this request"));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/identity-missing"))
                .andExpect(jsonPath("$.detail").value("No organization was supplied with this request"));
    }
}
