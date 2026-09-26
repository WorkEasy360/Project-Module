package com.projectmodule.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    /**
     * The deployed site. Saving a project from https://ops.workeasy360.com failed with
     * "Invalid CORS request" while reading worked, because a browser omits the Origin header on a
     * same-origin GET but always sends it on a write. The request therefore arrived carrying an
     * Origin the allowlist did not contain.
     */
    @ParameterizedTest(name = "{0} may call the API")
    @ValueSource(strings = {"https://workeasy360.com", "https://ops.workeasy360.com"})
    @DisplayName("the deployed site's origins are allowed")
    void allowsDeployedOrigins(String origin) throws Exception {
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenReturn(new DashboardResponse(0L, 0L, byStatus(), byPriority()));

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    /**
     * The exact exchange a browser performs before "save project": a preflight for a POST that
     * carries a JSON body and the caller-identity headers. This is the request that was being
     * refused, so it is asserted end to end rather than only the simple-request case above.
     */
    @Test
    @DisplayName("the preflight a browser sends before saving a project is approved")
    void allowsPreflightForSavingAProject() throws Exception {
        mockMvc.perform(options("/api/v1/projects")
                        .header(HttpHeaders.ORIGIN, "https://ops.workeasy360.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-user-id,x-org-id,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://ops.workeasy360.com"))
                .andExpect(header().stringValues("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("POST"))));
    }

    /**
     * Credentials stay off. This API is authorised by the X-User-Id / X-Org-Id headers the
     * upstream gateway supplies, not by cookies or HTTP authentication, and the browser client
     * never sets {@code credentials: 'include'}. Switching credentials on would tell browsers to
     * attach cookies to cross-origin calls, widening exposure for no functional gain, so its
     * absence is asserted rather than left to chance.
     */
    @Test
    @DisplayName("credentialed cross-origin requests are not enabled")
    void doesNotAllowCredentials() throws Exception {
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenReturn(new DashboardResponse(0L, 0L, byStatus(), byPriority()));

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.ORIGIN, "https://ops.workeasy360.com"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
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
