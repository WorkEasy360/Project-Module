package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoFlowTestBase;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * The two ways in, through the application's real security chain: an email sign-in's bearer token (main's account
 * package, here a stand-in {@link BearerTokenAuthenticator}) and a Workeasy360 sign-in session (the fake provider).
 * Workeasy360 is configured and the mode is the default, {@code sso_optional}.
 */
@DisplayName("Email sign-in alongside Workeasy360 sign-in")
class EmailSignInAlongsideWorkeasy360Test extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";
    private static final ExternalUserId ACCOUNT = ExternalUserId.of(UUID.fromString("5a0c7a5e-1111-4222-8333-444455556666"));
    private static final OrganizationId ACCOUNT_ORG =
            OrganizationId.of(UUID.fromString("5a0c7a5e-aaaa-4bbb-8ccc-ddddeeeeffff"));

    @MockitoBean
    private BearerTokenAuthenticator accounts;

    @Autowired
    private SignInOptions signInOptions;

    @BeforeEach
    void tokens() {
        when(accounts.authenticate(anyString())).thenReturn(Optional.empty());
        when(accounts.authenticate("good-token")).thenReturn(Optional.of(new AuthenticatedIdentity(ACCOUNT, ACCOUNT_ORG)));
    }

    private MockHttpSession workeasy360Session(String subject) throws Exception {
        MvcResult result = signInFresh("organization=" + ORG, spec -> spec.subject = subject);
        assertThat(ssoError(result)).isNull();
        return sessionOf(result);
    }

    @Test
    @DisplayName("Workeasy360 is offered and is not the only way in")
    void options() {
        assertThat(signInOptions.workeasy360Enabled()).isTrue();
        assertThat(signInOptions.ssoOnly()).isFalse();
        assertThat(signInOptions.describe()).containsEntry("enabled", true).containsEntry("signInMode", "sso_optional");
    }

    @Test
    @DisplayName("a valid bearer token is its account on the API; writes need no CSRF token or same-origin proof")
    void bearerTokenIsTheAccount() throws Exception {
        mvc.perform(get("/api/v1/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(ACCOUNT.toString()))
                .andExpect(jsonPath("$.organization").value(ACCOUNT_ORG.value().toString()));
        mvc.perform(post("/api/v1/test/write").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isNoContent());
        // A cross-origin page with a token it was given (an allowed CORS origin) is not a cookie-riding forgery.
        mvc.perform(post("/api/v1/test/write").header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .header("Sec-Fetch-Site", "same-site"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a dead bearer token is a 401 on reads AND writes (never a CSRF 403), so the app signs the person out")
    void deadTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/identity-missing"));
        mvc.perform(post("/api/v1/test/write").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a bearer token never borrows a Workeasy360 session sent with it, and never reaches /api/auth/**")
    void bearerTokenAndSessionDoNotMix() throws Exception {
        String person = UUID.randomUUID().toString();
        MockHttpSession session = workeasy360Session(person);

        mvc.perform(get("/api/v1/test/whoami").session(session).header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(jsonPath("$.user").value(ACCOUNT.toString()));
        mvc.perform(get("/api/v1/test/whoami").session(session).header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());
        // Without a token the session is still the person's: it was not touched.
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(jsonPath("$.user").value(person));
        // The session read is Workeasy360's alone.
        mvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a Workeasy360 session still needs CSRF and same-origin for writes")
    void cookieWritesStillNeedCsrf() throws Exception {
        MockHttpSession session = workeasy360Session(UUID.randomUUID().toString());

        mvc.perform(post("/api/v1/test/write").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
        var csrf = csrfCookie(session);
        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .header("Sec-Fetch-Site", "same-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("origin_not_allowed"));
    }

    @Test
    @DisplayName("Workeasy360's entitlement is asked about Workeasy360 sign-ins only, never about a bearer token")
    void entitlementIsForWorkeasy360SignInsOnly() throws Exception {
        FAKE.entitlementHandler = request -> FakeWorkeasy360.EntitlementReply.deny("expired");

        mvc.perform(get("/api/v1/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk());
        assertThat(FAKE.entitlementRequests).isEmpty();

        MockHttpSession session = workeasy360Session(UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/test/whoami").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_access_ended"));
    }

    @Test
    @DisplayName("the email sign-in endpoints are public and need no CSRF token (they return a token, set no cookie)")
    void emailSignInEndpointsArePublic() throws Exception {
        // This test application has no account controllers, so "the chain let it through" is whatever the
        // dispatcher answers for an unmapped path (404 or 405), never the chain's 401 or 403.
        for (String path : new String[] {"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/google",
                "/api/v1/auth/legacy/claim", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                "/api/v1/auth/invitation/preview", "/api/v1/auth/invitation/accept", "/api/v1/auth/logout"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(passedTheChain());
        }
        mvc.perform(get("/api/v1/auth/config")).andExpect(passedTheChain());
        // Everything else under /api/v1/auth still needs someone signed in.
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        var csrf = csrfCookie(new MockHttpSession());
        mvc.perform(post("/api/v1/auth/change-password").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a Workeasy360 sign-in has no email account here: its account endpoints say so with a 403, not a 401")
    void accountEndpointsAreManagedInWorkeasy360() throws Exception {
        MockHttpSession session = workeasy360Session(UUID.randomUUID().toString());
        var csrf = csrfCookie(session);

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("managed_in_workeasy360"));
        mvc.perform(patch("/api/v1/auth/me").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(jsonPath("$.code").value("managed_in_workeasy360"));
        mvc.perform(get("/api/v1/workspace/invitations").session(session))
                .andExpect(jsonPath("$.code").value("managed_in_workeasy360"));
        mvc.perform(post("/api/v1/workspace/people/" + UUID.randomUUID() + "/reset-link").session(session)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(jsonPath("$.code").value("managed_in_workeasy360"));
        // The people list is not account-only (it answers with the organization's email accounts, if any).
        mvc.perform(get("/api/v1/workspace/people").session(session)).andExpect(passedTheChain());
        // An email sign-in's own account endpoints are its controllers' business.
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(passedTheChain());
    }

    private static ResultMatcher passedTheChain() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("refused by the security chain: " + result.getResponse().getContentAsString())
                .isNotIn(401, 403);
    }
}
