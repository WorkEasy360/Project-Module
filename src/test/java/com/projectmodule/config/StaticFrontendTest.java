package com.projectmodule.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.api.GlobalExceptionHandler;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.dashboard.api.DashboardController;
import com.projectmodule.dashboard.api.dto.DashboardResponse;
import com.projectmodule.dashboard.application.DashboardApplicationService;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The React frontend is built by the Maven build and packaged into the jar under {@code /static},
 * so one deployment serves both the UI and the API. These tests hold that arrangement in place:
 * the UI is reachable, and serving it has not started swallowing API traffic or errors.
 *
 * <p>The application uses {@code HashRouter}, so every in-app route is a fragment
 * ({@code /#/projects}) which a browser never sends to the server. The server therefore only ever
 * needs to serve {@code /}; there is deliberately no catch-all that rewrites unknown paths to
 * {@code index.html}, and these tests prove an unknown path still produces a real API error.
 *
 * <p>Skipped when the build ran with {@code -DskipFrontend=true}, rather than passing vacuously.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({ApiConfiguration.class, GlobalExceptionHandler.class})
class StaticFrontendTest {

    /** Matches the hashed bundle that the built index.html references. */
    private static final Pattern ASSET_REFERENCE = Pattern.compile("/assets/[A-Za-z0-9._-]+\\.js");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardApplicationService dashboardApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    @BeforeEach
    void requirePackagedFrontend() {
        Assumptions.assumeTrue(
                new ClassPathResource("static/index.html").exists(),
                "frontend assets are not on the classpath (build ran with -DskipFrontend=true)");
    }

    @Test
    @DisplayName("the root URL serves the frontend entry page")
    void rootServesFrontend() throws Exception {
        // Spring Boot's welcome-page handling serves a packaged index.html by forwarding to it.
        // MockMvc records the forward rather than executing it, so the forward target is what
        // proves the wiring; the file it points at is asserted below.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        assertThat(packagedIndexHtml())
                .as("packaged entry page should be the built React app")
                .contains("<div id=\"root\">")
                .contains("<title>");
    }

    @Test
    @DisplayName("the asset the entry page references is served from the packaged build")
    void servesReferencedAsset() throws Exception {
        String assetPath = referencedAssetPath();

        mockMvc.perform(get(assetPath))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function")));
    }

    @Test
    @DisplayName("an existing API route still answers normally")
    void apiRouteStillWorks() throws Exception {
        Map<ProjectStatus, Long> byStatus = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            byStatus.put(status, 0L);
        }
        Map<ProjectPriority, Long> byPriority = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority priority : ProjectPriority.values()) {
            byPriority.put(priority, 0L);
        }
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenReturn(new DashboardResponse(2L, 0L, byStatus, byPriority));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.activeProjectCount").value(2));
    }

    @Test
    @DisplayName("an unknown API route returns an API error, never the frontend HTML")
    void unknownApiRouteIsNotSwallowedByTheFrontend() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<div id=\"root\">"))));
    }

    @Test
    @DisplayName("an unknown non-API path is still a real error, not a silent index.html")
    void unknownPathIsNotRewrittenToIndex() throws Exception {
        // HashRouter keeps app routes in the fragment, so nothing legitimate arrives here.
        mockMvc.perform(get("/not-a-real-page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"));
    }

    @Test
    @DisplayName("serving the frontend does not relax the identity requirement")
    void identityIsStillRequired() throws Exception {
        when(dashboardApplicationService.getDashboard(requestContext))
                .thenThrow(new MissingIdentityException("No organization was supplied with this request"));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/identity-missing"));
    }

    /** The packaged entry page, read from the same classpath location the jar serves it from. */
    private static String packagedIndexHtml() throws IOException {
        return new String(
                new ClassPathResource("static/index.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    /** Reads the packaged index.html and returns the bundle path it points at. */
    private static String referencedAssetPath() throws IOException {
        Matcher matcher = ASSET_REFERENCE.matcher(packagedIndexHtml());
        assertThat(matcher.find())
                .as("packaged index.html should reference a built JavaScript bundle")
                .isTrue();
        return matcher.group();
    }
}
