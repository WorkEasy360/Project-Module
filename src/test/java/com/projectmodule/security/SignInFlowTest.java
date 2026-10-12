package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoFlowTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Sign in with Workeasy360: the happy path")
class SignInFlowTest extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";

    @Test
    @DisplayName("start redirects with state, nonce, S256 PKCE, the registered redirect URI and scopes")
    void startBuildsTheAuthorizationRequest() throws Exception {
        Started started = start("organization=" + ORG);
        FakeWorkeasy360.Authorization request = started.authorization();

        assertThat(request.responseType()).isEqualTo("code");
        assertThat(request.clientId()).isEqualTo(FakeWorkeasy360.CLIENT_ID);
        assertThat(request.redirectUri()).isEqualTo(FakeWorkeasy360.REDIRECT_URI);
        assertThat(request.codeChallengeMethod()).isEqualTo("S256");
        assertThat(request.codeChallenge()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(request.state()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(request.nonce()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(request.scope()).isEqualTo("openid email profile organization");
        assertThat(request.organization()).isEqualTo(ORG);
    }

    @Test
    @DisplayName("a full sign-in creates a session pinned to the token's organization and lands on the app")
    void fullSignIn() throws Exception {
        String subject = UUID.randomUUID().toString();
        MvcResult result = signInFresh("organization=" + ORG, spec -> {
            spec.subject = subject;
            spec.email = "pat@example.com";
            spec.name = "Pat";
        });

        assertThat(location(result)).isEqualTo("/");
        MockHttpSession session = sessionOf(result);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                // Workeasy360's sub is its user id, so it is also the internal user id.
                .andExpect(jsonPath("$.user.id").value(subject))
                .andExpect(jsonPath("$.user.email").value("pat@example.com"))
                .andExpect(jsonPath("$.user.name").value("Pat"))
                .andExpect(jsonPath("$.organization.id").value(ORG))
                .andExpect(jsonPath("$.workeasy360SignOut").value(true));
        assertThat(FAKE.userinfoCalls).as("the userinfo endpoint is never called").isZero();
        assertThat(FAKE.assertionProblems).isEmpty();
        assertThat(FAKE.tokenRequests).hasSize(1);
        assertThat(FAKE.tokenRequests.get(0).get("redirect_uri")).isEqualTo(FakeWorkeasy360.REDIRECT_URI);
    }
}
