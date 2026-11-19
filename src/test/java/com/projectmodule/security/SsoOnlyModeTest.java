package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.SsoFlowTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code WORKEASY360_SIGN_IN_MODE=sso_only}: Workeasy360 is the only way in, so the email sign-in endpoints refuse
 * to start a sign-in (or create an account), and Workeasy360 sign-in works exactly as before.
 */
@TestPropertySource(properties = "workeasy360.sign-in-mode=sso_only")
@DisplayName("sso_only: Workeasy360 is the only way in")
class SsoOnlyModeTest extends SsoFlowTestBase {

    @Autowired
    private SignInOptions signInOptions;

    @Test
    @DisplayName("the mode is sso_only, and the sign-in page is told so")
    void mode() {
        assertThat(signInOptions.ssoOnly()).isTrue();
        assertThat(signInOptions.describe()).containsEntry("enabled", true).containsEntry("signInMode", "sso_only");
    }

    @Test
    @DisplayName("every email sign-in endpoint answers 403 sign_in_with_workeasy360")
    void emailSignInIsRefused() throws Exception {
        for (String path : new String[] {"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/google",
                "/api/v1/auth/legacy/claim", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                "/api/v1/auth/invitation/preview", "/api/v1/auth/invitation/accept"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("sign_in_with_workeasy360"));
        }
        // Signing out of an email session that still exists is not a sign-in, and stays possible.
        mvc.perform(post("/api/v1/auth/logout")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    @DisplayName("Workeasy360 sign-in is unchanged")
    void workeasy360SignInWorks() throws Exception {
        String person = UUID.randomUUID().toString();
        MvcResult result = signInFresh(null, spec -> spec.subject = person);

        assertThat(ssoError(result)).isNull();
        mvc.perform(get("/api/v1/test/whoami").session(sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(person));
    }
}
