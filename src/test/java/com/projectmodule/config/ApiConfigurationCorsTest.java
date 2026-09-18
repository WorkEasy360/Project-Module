package com.projectmodule.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.dashboard.api.DashboardController;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the frontend's origin is actually allowed through by {@link ApiConfiguration}'s CORS
 * mapping — without this, a browser-based SPA would have every request blocked before it reaches
 * a controller, even though a non-browser client (curl, MockMvc without an Origin header) would
 * see no difference. Uses the same {@code @WebMvcTest} + real {@link ApiConfiguration} pattern as
 * every other controller slice test, so the CORS registration under test is the real one, not a
 * hand-rolled substitute.
 */
@WebMvcTest(DashboardController.class)
@Import(ApiConfiguration.class)
class ApiConfigurationCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardApplicationService dashboardApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @Test
    @DisplayName("a request from the default frontend dev origin is allowed and echoed back")
    void allowsConfiguredFrontendOrigin() throws Exception {
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenReturn(new DashboardResponse(0L, 0L, byStatus(), byPriority()));

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("a request from an unrecognized origin is rejected before reaching the controller")
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    private static Map<ProjectStatus, Long> byStatus() {
        Map<ProjectStatus, Long> map = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            map.put(status, 0L);
        }
        return map;
    }

    private static Map<ProjectPriority, Long> byPriority() {
        Map<ProjectPriority, Long> map = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority priority : ProjectPriority.values()) {
            map.put(priority, 0L);
        }
        return map;
    }
}
