package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.identity.FederatedIdentityStore;
import com.projectmodule.security.identity.JpaFederatedIdentityStore;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application (real controllers, real PostgreSQL): the security chain is active on
 * {@code /api/v1/**}, the retired identity headers open nothing, and the PostgreSQL identity store is the one
 * wired. Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, like the other full-context tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class FullApplicationSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("every real API route needs a session; the old identity headers open nothing")
    void apiRequiresASession() throws Exception {
        String user = UUID.randomUUID().toString();
        String organization = UUID.randomUUID().toString();

        for (String path : new String[] {"/api/v1/projects", "/api/v1/dashboard", "/api/v1/templates",
                "/api/v1/projects/" + UUID.randomUUID() + "/tasks"}) {
            mvc.perform(get(path).header("X-User-Id", user).header("X-Org-Id", organization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("https://errors.projectmodule/identity-missing"));
        }
        // A write with a "valid" CSRF-less request is refused before it can reach a controller.
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-User-Id", user).header("X-Org-Id", organization))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health is public; the rest of the actuator and the API documents are not")
    void publicSurface() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the PostgreSQL identity store is wired, and no default user or password exists")
    void wiring() {
        assertThat(context.getBean(FederatedIdentityStore.class)).isInstanceOf(JpaFederatedIdentityStore.class);
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    @DisplayName("an unmapped path is still a 404 problem document, never the app HTML or a sign-in page")
    void unmappedPathIsStillNotFound() throws Exception {
        mvc.perform(get("/not-a-real-page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/not-found"));
        mvc.perform(get("/login")).andExpect(status().isNotFound());
        mvc.perform(get("/oauth2/authorization/workeasy360")).andExpect(status().isNotFound());
    }
}
