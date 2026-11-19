package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.SsoFlowTestBase;
import com.projectmodule.security.web.SignedOutMarker;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration guide section 6b on the server's side: opening Easy Project with no session starts a sign-in
 * by itself (that part is the single-page app's route guard), unless this browser signed out of Easy Project
 * since it last signed in. The marker for that is a session cookie left by the logout and cleared by a
 * successful sign-in. The API is never redirected; {@code next} is the same validated same-site route.
 */
@DisplayName("Opening Easy Project directly: the signed-out marker, the API, and next")
class SignedOutMarkerTest extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";
    private static final String MARKER = "__Host-ep_signed_out";

    private MockHttpSession signedIn() throws Exception {
        MvcResult result = signInFresh("organization=" + ORG, spec -> spec.subject = UUID.randomUUID().toString());
        assertThat(ssoError(result)).isNull();
        return sessionOf(result);
    }

    private MvcResult logout(MockHttpSession session) throws Exception {
        Cookie csrf = csrfCookie(session);
        return mvc.perform(post("/api/auth/logout").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    /** The Set-Cookie header lines of the response that are about the marker. */
    private static List<String> markerHeaders(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(line -> line.startsWith(MARKER + "=")).toList();
    }

    // --- set by both sign-outs ---------------------------------------------------------------------------------

    @Test
    @DisplayName("sign out of Easy Project leaves the marker: a session cookie the app can read, not HttpOnly")
    void signOutLeavesTheMarker() throws Exception {
        MvcResult result = logout(signedIn());

        List<String> lines = markerHeaders(result);
        assertThat(lines).hasSize(1);
        String line = lines.get(0);
        assertThat(line).startsWith(MARKER + "=1");
        assertThat(line).contains("Path=/").contains("Secure").contains("SameSite=Lax");
        assertThat(line).as("a session cookie: it ends with the browser session").doesNotContain("Max-Age")
                .doesNotContain("Expires").doesNotContain("Domain");
        assertThat(line).as("the single-page app has to read it").doesNotContain("HttpOnly");
        // The Easy Project session cookie itself is still deleted, as before.
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(l -> l.startsWith("__Host-ep_session=")
                && l.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("sign out of Easy Project and Workeasy360 starts with that same logout, so it leaves the marker too")
    void signOutEverywhereLeavesTheMarkerBeforeLeaving() throws Exception {
        MockHttpSession session = signedIn();

        // The app's sequence: POST /api/auth/logout, then navigate to /api/auth/workeasy360/logout.
        MvcResult logout = logout(session);
        MvcResult endSession = mvc.perform(get("/api/auth/workeasy360/logout")).andReturn();

        assertThat(markerHeaders(logout)).hasSize(1);
        assertThat(endSession.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(endSession)).startsWith(FAKE.endSessionEndpoint());
        assertThat(markerHeaders(endSession)).as("a link to the end-session redirect changes nothing locally").isEmpty();
    }

    @Test
    @DisplayName("a logout that is refused (no CSRF token) leaves no marker and keeps the session")
    void refusedLogoutLeavesNothing() throws Exception {
        MockHttpSession session = signedIn();

        MvcResult refused = mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden())
                .andReturn();

        assertThat(markerHeaders(refused)).isEmpty();
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
    }

    // --- cleared by a sign-in, and only by a successful one ------------------------------------------------------

    @Test
    @DisplayName("a successful sign-in clears the marker")
    void signInClearsTheMarker() throws Exception {
        Started started = start("organization=" + ORG);
        String code = FAKE.issueCode(started.authorization(), spec -> spec.subject = UUID.randomUUID().toString());

        MvcResult result = mvc.perform(get("/api/auth/callback/workeasy360")
                        .param("code", code).param("state", started.authorization().state())
                        .session(started.session()).cookie(new Cookie(MARKER, "1")))
                .andReturn();

        assertThat(ssoError(result)).isNull();
        List<String> lines = markerHeaders(result);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith(MARKER + "=;").contains("Max-Age=0").contains("Path=/").contains("Secure");
    }

    @Test
    @DisplayName("a sign-in that fails does not clear the marker")
    void failedSignInKeepsTheMarker() throws Exception {
        Started started = start("organization=" + ORG);

        MvcResult cancelled = mvc.perform(get("/api/auth/callback/workeasy360")
                        .param("error", "access_denied").param("state", started.authorization().state())
                        .session(started.session()).cookie(new Cookie(MARKER, "1")))
                .andReturn();

        assertThat(ssoError(cancelled)).isEqualTo("cancelled");
        assertThat(markerHeaders(cancelled)).isEmpty();
    }

    @Test
    @DisplayName("without the secure cookie setting (the local profile) the marker has the plain name and no Secure flag")
    void plainNameOverHttp() {
        SignedOutMarker marker = new SignedOutMarker(false);
        MockHttpServletResponse set = new MockHttpServletResponse();
        MockHttpServletResponse cleared = new MockHttpServletResponse();

        marker.set(set);
        marker.clear(cleared);

        assertThat(marker.cookieName()).isEqualTo("ep_signed_out");
        assertThat(set.getHeader("Set-Cookie")).startsWith("ep_signed_out=1").contains("Path=/").doesNotContain("Secure")
                .doesNotContain("Max-Age").doesNotContain("HttpOnly");
        assertThat(cleared.getHeader("Set-Cookie")).startsWith("ep_signed_out=;").contains("Max-Age=0");
    }

    // --- what the marker does not touch ------------------------------------------------------------------------

    @Test
    @DisplayName("the launcher still starts a sign-in for its organization when the marker is present: it is an explicit choice")
    void launcherIgnoresTheMarker() throws Exception {
        MvcResult result = mvc.perform(get("/").param("organization", ORG).cookie(new Cookie(MARKER, "1"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(result)).isEqualTo("/api/auth/workeasy360/start?organization=" + ORG);
    }

    @Test
    @DisplayName("the server never redirects the root by itself, with or without the marker: the app's route guard does")
    void rootIsNotRedirectedByTheServer() throws Exception {
        for (MvcResult result : new MvcResult[] {
                mvc.perform(get("/")).andReturn(),
                mvc.perform(get("/").cookie(new Cookie(MARKER, "1"))).andReturn()}) {
            assertThat(result.getResponse().getHeader("Location")).isNull();
            assertThat(result.getResponse().getStatus()).isNotEqualTo(302);
        }
    }

    // --- the API is never redirected ------------------------------------------------------------------------------

    @Test
    @DisplayName("/api stays 401 JSON for a browser with no session, even one that asks for a page, with or without the marker")
    void apiIsNeverRedirected() throws Exception {
        for (String path : List.of("/api/v1/test/whoami", "/api/v1/projects", "/api/auth/session", "/api/anything")) {
            for (boolean marker : new boolean[] {false, true}) {
                var request = get(path).accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON)
                        .header("Sec-Fetch-Mode", "navigate").header("Sec-Fetch-Dest", "document");
                if (marker) {
                    request = request.cookie(new Cookie(MARKER, "1"));
                }
                MvcResult result = mvc.perform(request).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.status").value(401)).andReturn();

                assertThat(result.getResponse().getHeader("Location")).as(path).isNull();
                assertThat(result.getResponse().getContentType()).as(path).contains("json");
            }
        }
    }

    // --- next ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an automatic start has no organization hint, and returns to the route it was opened on")
    void automaticStartCarriesNextOnly() throws Exception {
        Started started = startWith("next", "/projects/9b2f/tasks?status=OPEN");

        assertThat(started.authorization().organization()).as("no organization hint is forwarded").isNull();
        assertThat(location(signIn(started, spec -> spec.subject = UUID.randomUUID().toString())))
                .isEqualTo("/#/projects/9b2f/tasks?status=OPEN");
    }

    @Test
    @DisplayName("a hostile next never leaves the site: every form lands on the home page")
    void hostileNextIsNeverForwarded() throws Exception {
        for (String hostile : List.of("//evil.example/x", "https://evil.example/", "/\\evil.example", "/%2F/evil.example",
                "javascript:alert(1)", "/\t/evil.example")) {
            Started started = startWith("next", hostile);
            String landing = location(signIn(started, spec -> spec.subject = UUID.randomUUID().toString()));
            assertThat(landing).as(hostile).isEqualTo("/");
        }
    }
}
