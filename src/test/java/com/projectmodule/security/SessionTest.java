package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoFlowTestBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("The session: fixation, account switching, identity, CSRF, sign-out")
class SessionTest extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";
    private static final String OTHER_ORG = "0e5b1f3a-9a4e-4d2c-8c11-5b7a1c2d3e4f";

    private MockHttpSession signedIn(String subject, String organization) throws Exception {
        MvcResult result = signInFresh("organization=" + organization, spec -> spec.subject = subject);
        assertThat(ssoError(result)).isNull();
        return sessionOf(result);
    }

    // --- fixation and switching ---------------------------------------------------------------------

    @Test
    @DisplayName("sign-in issues a new session id and carries nothing over from the one that held the flow")
    void sessionFixation() throws Exception {
        Started started = start("organization=" + ORG);
        MockHttpSession before = started.session();
        before.setAttribute("planted-by-an-attacker", "yes");

        MvcResult result = signIn(started, spec -> spec.subject = UUID.randomUUID().toString());
        MockHttpSession after = sessionOf(result);

        assertThat(after.getId()).isNotEqualTo(before.getId());
        assertThat(before.isInvalid()).as("the old session is gone").isTrue();
        assertThat(after.getAttribute("planted-by-an-attacker")).isNull();
        mvc.perform(get("/api/auth/session").session(after)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a fixed session id from before the sign-in cannot be used afterwards")
    void fixedSessionIsUseless() throws Exception {
        Started started = start(null);
        String fixedId = started.session().getId();
        signIn(started, spec -> spec.subject = UUID.randomUUID().toString());

        // The attacker still holds the id they planted; it is no session any more.
        mvc.perform(get("/api/v1/test/whoami").session(started.session())).andExpect(status().isUnauthorized());
        assertThat(started.session().getId()).isEqualTo(fixedId);
    }

    @Test
    @DisplayName("a different person signing in in the same browser replaces the first one's session (account switching)")
    void accountSwitching() throws Exception {
        String personA = UUID.randomUUID().toString();
        String personB = UUID.randomUUID().toString();
        MockHttpSession sessionA = signedIn(personA, ORG);
        mvc.perform(get("/api/v1/test/whoami").session(sessionA)).andExpect(jsonPath("$.user").value(personA));

        // Person B starts and finishes a sign-in in the browser that holds A's session.
        Started started = start(sessionA, "organization=" + OTHER_ORG);
        MvcResult result = signIn(started, spec -> spec.subject = personB);
        MockHttpSession sessionB = sessionOf(result);

        assertThat(sessionB.getId()).isNotEqualTo(sessionA.getId());
        assertThat(sessionA.isInvalid()).as("A's session was invalidated first").isTrue();
        mvc.perform(get("/api/v1/test/whoami").session(sessionA)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/test/whoami").session(sessionB))
                .andExpect(jsonPath("$.user").value(personB))
                .andExpect(jsonPath("$.organization").value(OTHER_ORG));
    }

    @Test
    @DisplayName("a sign-in that fails leaves the person who is signed in signed in")
    void failedSignInKeepsTheCurrentSession() throws Exception {
        String personA = UUID.randomUUID().toString();
        MockHttpSession sessionA = signedIn(personA, ORG);

        Started started = start(sessionA, "organization=" + OTHER_ORG);
        MvcResult result = signIn(started, spec -> spec.organizationId = ORG); // wrong organization

        assertThat(ssoError(result)).isEqualTo("wrong_organization");
        mvc.perform(get("/api/v1/test/whoami").session(sessionA)).andExpect(jsonPath("$.user").value(personA));
    }

    // --- identity -------------------------------------------------------------------------------------

    @Test
    @DisplayName("the identity headers are ignored: no session, no access, whatever they claim")
    void identityHeadersAreIgnored() throws Exception {
        mvc.perform(get("/api/v1/dashboard")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Org-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/identity-missing"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("with a session, headers naming somebody else change nothing: the request is the session's person")
    void headersCannotOverrideTheSession() throws Exception {
        String person = UUID.randomUUID().toString();
        MockHttpSession session = signedIn(person, ORG);

        mvc.perform(get("/api/v1/test/whoami").session(session)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Org-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(person))
                .andExpect(jsonPath("$.organization").value(ORG));
    }

    @Test
    @DisplayName("the organization is pinned to the token: nothing sent afterwards can change it")
    void organizationIsPinned() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);

        mvc.perform(get("/api/v1/test/whoami").session(session).param("organization", OTHER_ORG)
                        .param("organizationId", OTHER_ORG).header("X-Org-Id", OTHER_ORG))
                .andExpect(jsonPath("$.organization").value(ORG));
    }

    @Test
    @DisplayName("the real security chain protects /api/v1/**: dashboard, API root, unknown paths")
    void apiRequiresASession() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/no-such-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()); // CSRF is checked before the missing session
    }

    @Test
    @DisplayName("an anonymous request to the API does not create a session (no saved-request cache, no unbounded sessions)")
    void anonymousApiRequestsCreateNoSession() throws Exception {
        MvcResult dashboard = mvc.perform(get("/api/v1/dashboard")).andReturn();
        MvcResult session = mvc.perform(get("/api/auth/session")).andReturn();
        MvcResult docs = mvc.perform(get("/v3/api-docs")).andReturn();

        assertThat(dashboard.getRequest().getSession(false)).isNull();
        assertThat(session.getRequest().getSession(false)).isNull();
        assertThat(docs.getRequest().getSession(false)).isNull();
        assertThat(dashboard.getResponse().getHeader("Set-Cookie") == null
                || !dashboard.getResponse().getHeader("Set-Cookie").contains("ep_session")).isTrue();
    }

    @Test
    @DisplayName("the session that holds a pending sign-in lives ten minutes; a signed-in browser keeps its own")
    void preSignInSessionIsShortLived() throws Exception {
        Started started = start(null);
        assertThat(started.session().getMaxInactiveInterval()).isEqualTo(600);

        MvcResult result = signIn(started, spec -> spec.subject = UUID.randomUUID().toString());
        MockHttpSession signedIn = sessionOf(result);
        assertThat(signedIn.getMaxInactiveInterval()).as("the signed-in session is a new one").isNotEqualTo(600);

        signedIn.setMaxInactiveInterval(28_800);
        start(signedIn, "organization=" + OTHER_ORG); // someone signed in starts another sign-in
        assertThat(signedIn.getMaxInactiveInterval()).isEqualTo(28_800);
    }

    @Test
    @DisplayName("every API document form is behind sign-in, including the .yaml one")
    void apiDocumentsAreProtected() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/swagger-ui.html", "/swagger-ui/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("health reveals no details, not even to a signed-in customer")
    void healthShowsNoDetails() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);

        for (MockHttpSession caller : new MockHttpSession[] {null, session}) {
            var request = get("/actuator/health");
            if (caller != null) {
                request.session(caller);
            }
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }
    }

    @Test
    @DisplayName("an authentication this application did not create is refused on the API, not passed through")
    void foreignAuthenticationIsRefused() throws Exception {
        mvc.perform(get("/api/v1/test/whoami").with(authentication(
                        new UsernamePasswordAuthenticationToken("intruder", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("public: the health probe and the single-page app are reachable without a session")
    void publicEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        MvcResult root = mvc.perform(get("/")).andReturn();
        assertThat(root.getResponse().getStatus()).as("the app's entry page is not behind sign-in").isNotEqualTo(401);
    }

    @Test
    @DisplayName("the session read describes who is signed in and whether 'sign out of Workeasy360 too' is offered")
    void sessionRead() throws Exception {
        String person = UUID.randomUUID().toString();
        MvcResult result = signInFresh("organization=" + ORG, spec -> {
            spec.subject = person;
            spec.email = null;
            spec.name = null;
        });

        mvc.perform(get("/api/auth/session").session(sessionOf(result)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(person))
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.organization.id").value(ORG))
                .andExpect(jsonPath("$.workeasy360SignOut").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.idToken").doesNotExist());
    }

    // --- provisioning -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a sub that is a UUID is the user id; the same sub is the same user at every sign-in")
    void uuidSubjectIsTheUserId() throws Exception {
        String sub = UUID.randomUUID().toString();

        MockHttpSession first = signedIn(sub, ORG);
        MockHttpSession second = signedIn(sub, OTHER_ORG); // another browser, same person

        mvc.perform(get("/api/v1/test/whoami").session(first)).andExpect(jsonPath("$.user").value(sub));
        mvc.perform(get("/api/v1/test/whoami").session(second)).andExpect(jsonPath("$.user").value(sub));
        assertThat(identities.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("a sub that is not a UUID gets a stable generated user id; e-mail is never what identifies")
    void opaqueSubjectGetsAGeneratedUserId() throws Exception {
        MvcResult one = signInFresh(null, spec -> {
            spec.subject = "auth0|abc123";
            spec.email = "same@example.com";
        });
        MvcResult again = signInFresh(null, spec -> {
            spec.subject = "auth0|abc123";
            spec.email = "changed@example.com";
        });
        MvcResult another = signInFresh(null, spec -> {
            spec.subject = "auth0|different";
            spec.email = "same@example.com";
        });

        String first = userOf(sessionOf(one));
        assertThat(first).isNotEqualTo("auth0|abc123");
        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(userOf(sessionOf(again))).isEqualTo(first);
        assertThat(userOf(sessionOf(another))).isNotEqualTo(first);
    }

    private String userOf(MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/v1/test/whoami").session(session)).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("user").asText();
    }

    // --- landing --------------------------------------------------------------------------------------------

    static Stream<Arguments> nextValues() {
        return Stream.of(
                Arguments.of("/projects/9b2f", "/#/projects/9b2f"),
                Arguments.of("/dashboard", "/#/dashboard"),
                Arguments.of("/projects/1/tasks?status=OPEN", "/#/projects/1/tasks?status=OPEN"),
                Arguments.of("/", "/"),
                Arguments.of("", "/"),
                Arguments.of("//evil.example", "/"),
                Arguments.of("/\\evil.example", "/"),
                Arguments.of("/\t/evil.example", "/"),
                Arguments.of("/\n/evil.example", "/"),
                Arguments.of("https://evil.example", "/"),
                Arguments.of("javascript:alert(1)", "/"),
                Arguments.of("%2F%2Fevil.example", "/"),
                Arguments.of("/%2F/evil.example", "/"),
                Arguments.of("/%5Cevil.example", "/"),
                Arguments.of("/sign-in", "/"),
                Arguments.of("/api/auth/workeasy360/start", "/"));
    }

    @ParameterizedTest(name = "next={0} lands on {1}")
    @MethodSource("nextValues")
    @DisplayName("next is validated as a same-site route; the app lands on it as a hash route")
    void landing(String next, String expected) throws Exception {
        Started started = startWith("next", next);

        MvcResult result = signIn(started, spec -> spec.subject = UUID.randomUUID().toString());

        assertThat(location(result)).isEqualTo(expected);
    }

    @Test
    @DisplayName("the next of one flow does not leak into another started in the same browser")
    void nextIsPerFlow() throws Exception {
        Started one = startWith("next", "/projects/1");
        Started two = startWith(one.session(), "next", "/projects/2");

        assertThat(location(signIn(two, spec -> spec.subject = UUID.randomUUID().toString())))
                .isEqualTo("/#/projects/2");
    }

    // --- launcher -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the launcher: /?organization=<uuid> always starts a sign-in for that organization")
    void launcher() throws Exception {
        MvcResult result = mvc.perform(get("/").param("organization", ORG)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(result)).isEqualTo("/api/auth/workeasy360/start?organization=" + ORG);
    }

    @Test
    @DisplayName("the launcher starts a sign-in even when a session exists: the person picked that organization")
    void launcherEvenWhenSignedIn() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);

        MvcResult result = mvc.perform(get("/").param("organization", OTHER_ORG).session(session)).andReturn();

        assertThat(location(result)).isEqualTo("/api/auth/workeasy360/start?organization=" + OTHER_ORG);
        // ...and the redirect it leads to is the provider's authorization endpoint, for that organization.
        Started follow = start(session, "organization=" + OTHER_ORG);
        assertThat(follow.authorization().organization()).isEqualTo(OTHER_ORG);
    }

    @Test
    @DisplayName("a plain / (or an organization that is not a UUID) does not start anything")
    void plainRootDoesNotAutoStart() throws Exception {
        for (MvcResult result : new MvcResult[] {
                mvc.perform(get("/")).andReturn(),
                mvc.perform(get("/").param("organization", "acme")).andReturn(),
                mvc.perform(get("/").param("organization", "")).andReturn()}) {
            String location = result.getResponse().getHeader("Location");
            assertThat(location == null || !location.contains("/api/auth/workeasy360/start")).isTrue();
        }
    }

    // --- CSRF -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a write needs the CSRF header that matches the cookie; reads do not")
    void csrfOnWrites() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);
        Cookie cookie = csrfCookie(session);
        assertThat(cookie.isHttpOnly()).as("the app must be able to read it").isFalse();

        mvc.perform(post("/api/v1/test/write").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(post("/api/v1/test/write").session(session).cookie(cookie).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/test/write").session(session).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a write whose Origin is not this origin is refused, even with a valid session and CSRF token")
    void crossOriginWritesAreRefused() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);
        Cookie csrf = csrfCookie(session);

        // Origins the CORS configuration does not list are refused by the CORS filter itself (403, no body)...
        for (String origin : new String[] {"https://evil.example", "http://sibling.localhost", "http://localhost:8081",
                "https://localhost", "null"}) {
            mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                            .header("X-XSRF-TOKEN", csrf.getValue()).header("Origin", origin))
                    .andExpect(status().isForbidden());
        }
        // ...and origins it does list (another page of the same site) are refused by this application.
        for (String origin : new String[] {"https://workeasy360.com", "https://ops.workeasy360.com",
                "http://localhost:5173"}) {
            mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                            .header("X-XSRF-TOKEN", csrf.getValue()).header("Origin", origin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("origin_not_allowed"));
        }
    }

    @Test
    @DisplayName("a write the browser says is cross-site or same-site (a sibling subdomain) is refused")
    void fetchMetadataIsHonoured() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);
        Cookie csrf = csrfCookie(session);

        for (String site : new String[] {"cross-site", "same-site"}) {
            mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                            .header("X-XSRF-TOKEN", csrf.getValue()).header("Sec-Fetch-Site", site))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("origin_not_allowed"));
        }
        // The app's own pages: same-origin (and the Origin they send is this origin).
        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).header("Sec-Fetch-Site", "same-origin")
                        .header("Origin", "http://localhost"))
                .andExpect(status().isNoContent());
        // Reads are not subject to it.
        mvc.perform(get("/api/v1/test/whoami").session(session).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the OAuth redirects are GETs: they are not blocked for want of a CSRF token")
    void oauthGetsAreNotWrites() throws Exception {
        mvc.perform(get("/api/auth/workeasy360/start")).andExpect(status().isFound());
        mvc.perform(get("/api/auth/callback/workeasy360").param("error", "access_denied")).andExpect(status().isFound());
    }

    @Test
    @DisplayName("the CSRF cookie is issued to a page that only reads, and is not tied to a stolen cookie value")
    void csrfCookieIsIssued() throws Exception {
        MvcResult anonymous = mvc.perform(get("/api/auth/session")).andReturn();

        assertThat(anonymous.getResponse().getCookie("__Host-XSRF-TOKEN")).isNotNull();
        assertThat(anonymous.getResponse().getHeader("Set-Cookie")).contains("Secure").contains("Path=/")
                .doesNotContain("Domain");
    }

    // --- sign-out -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/logout needs CSRF and ends the session server-side")
    void logout() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);
        Cookie cookie = csrfCookie(session);

        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").session(session).cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sign out of Easy Project and Workeasy360: redirect to the end-session endpoint, changing nothing locally")
    void endSessionRedirect() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString(), ORG);

        MvcResult result = mvc.perform(get("/api/auth/workeasy360/logout").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(result)).isEqualTo(FAKE.endSessionEndpoint()
                + "?client_id=" + encode(FakeWorkeasy360.CLIENT_ID)
                + "&post_logout_redirect_uri=" + encode(FakeWorkeasy360.POST_LOGOUT_URI));
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the end-session redirect works for a browser with no session, and falls back to / without an endpoint")
    void endSessionWithoutSessionOrEndpoint() throws Exception {
        MvcResult anonymous = mvc.perform(get("/api/auth/workeasy360/logout")).andReturn();
        assertThat(location(anonymous)).startsWith(FAKE.endSessionEndpoint());

        FAKE.advertiseEndSession = false;
        provider.forgetDiscovery();
        MvcResult none = mvc.perform(get("/api/auth/workeasy360/logout")).andReturn();
        assertThat(location(none)).isEqualTo("/");
    }

    @Test
    @DisplayName("if Workeasy360 cannot be described, sign-out of it falls back to /")
    void endSessionWhenWorkeasy360IsDown() throws Exception {
        FAKE.discoveryDown = true;
        provider.forgetDiscovery();

        MvcResult result = mvc.perform(get("/api/auth/workeasy360/logout")).andReturn();

        assertThat(location(result)).isEqualTo("/");
    }
}
