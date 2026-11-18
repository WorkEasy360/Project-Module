package com.projectmodule.security.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.projectmodule.dashboard.application.DashboardApplicationService;
import com.projectmodule.security.entitlement.EntitlementService;
import com.projectmodule.security.oidc.Workeasy360Provider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Drives complete sign-in flows through the application's real security chain with MockMvc, against the
 * {@link FakeWorkeasy360}: start (capture state, nonce and the PKCE challenge from the redirect), then the
 * callback in the same session, exactly as a browser would.
 */
// SsoTestApplication is a @TestConfiguration (so the real application's component scan never picks it
// up), and Spring Boot only skips its search for the real @SpringBootConfiguration when at least one
// configuration class is not a test one: the empty marker is that class.
@SpringBootTest(classes = {SsoTestApplication.class, SsoFlowCommon.NotATestConfiguration.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class SsoFlowCommon {

    /** See the note on the annotation above. */
    public static class NotATestConfiguration {
    }

    protected static final FakeWorkeasy360 FAKE = FakeWorkeasy360.shared();

    @DynamicPropertySource
    static void commonProperties(DynamicPropertyRegistry registry) {
        registry.add("workeasy360.issuer", FAKE::issuer);
        registry.add("workeasy360.client-id", () -> FakeWorkeasy360.CLIENT_ID);
        registry.add("workeasy360.redirect-uri", () -> FakeWorkeasy360.REDIRECT_URI);
        registry.add("workeasy360.post-logout-redirect-uri", () -> FakeWorkeasy360.POST_LOGOUT_URI);
        registry.add("workeasy360.http-timeout", () -> "3s");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected EntitlementService entitlements;

    @Autowired
    protected Workeasy360Provider provider;

    @Autowired
    protected SsoTestApplication.InMemoryIdentities identities;

    @MockitoBean
    protected DashboardApplicationService dashboardService;

    @BeforeEach
    void resetFake() {
        FAKE.reset();
        entitlements.clear();
        identities.clear();
        provider.forgetDiscovery();
    }

    /** A sign-in that has been started: the browser's session and what the provider was asked. */
    public record Started(MockHttpSession session, String location, FakeWorkeasy360.Authorization authorization) {
    }

    protected Started start(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/workeasy360/start" + (query == null ? "" : "?" + query)))
                .andReturn();
        return started(result, null);
    }

    /** Start with the parameters given as name, value pairs (so no value needs to be escaped by hand). */
    protected Started startWith(String... nameValuePairs) throws Exception {
        var request = get("/api/auth/workeasy360/start");
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return started(mvc.perform(request).andReturn(), null);
    }

    protected Started startWith(MockHttpSession existing, String... nameValuePairs) throws Exception {
        var request = get("/api/auth/workeasy360/start").session(existing);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return started(mvc.perform(request).andReturn(), existing);
    }

    protected Started start(MockHttpSession existing, String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/workeasy360/start" + (query == null ? "" : "?" + query))
                .session(existing)).andReturn();
        return started(result, existing);
    }

    private Started started(MvcResult result, MockHttpSession existing) {
        String location = result.getResponse().getHeader("Location");
        if (result.getResponse().getStatus() != 302 || location == null || !location.startsWith(FAKE.issuer())) {
            throw new AssertionError("start did not redirect to the provider: " + result.getResponse().getStatus()
                    + " " + location);
        }
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return new Started(session, location, FAKE.parse(location));
    }

    /** The callback a browser makes after the provider approved: code and state in the same session. */
    protected MvcResult callback(Started started, String code, String state) throws Exception {
        return mvc.perform(get("/api/auth/callback/workeasy360")
                        .param("code", code)
                        .param("state", state)
                        .session(started.session()))
                .andReturn();
    }

    /** Provider approves (default claims, then {@code customizer}); the browser comes back. */
    protected MvcResult signIn(Started started, Consumer<FakeWorkeasy360.IdTokenSpec> customizer) throws Exception {
        String code = FAKE.issueCode(started.authorization(), customizer);
        return callback(started, code, started.authorization().state());
    }

    /** Start and complete a sign-in in one go; returns the callback result. */
    protected MvcResult signInFresh(String query, Consumer<FakeWorkeasy360.IdTokenSpec> customizer) throws Exception {
        return signIn(start(query), customizer);
    }

    /** The session the callback left behind. */
    protected static MockHttpSession sessionOf(MvcResult callbackResult) {
        return (MockHttpSession) callbackResult.getRequest().getSession(false);
    }

    protected static String location(MvcResult result) {
        return result.getResponse().getHeader("Location");
    }

    /** The {@code sso_error} of a failure redirect, or null when it is not one. */
    protected static String ssoError(MvcResult result) {
        String location = location(result);
        if (location == null || !location.startsWith("/#/sign-in?sso_error=")) {
            return null;
        }
        return location.substring("/#/sign-in?sso_error=".length());
    }

    /** The CSRF cookie the application issues (any request does; a read of the session is the natural one). */
    protected Cookie csrfCookie(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/session").session(session)).andReturn();
        Cookie cookie = result.getResponse().getCookie("__Host-XSRF-TOKEN");
        if (cookie == null) {
            throw new AssertionError("no __Host-XSRF-TOKEN cookie was issued");
        }
        return cookie;
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    protected static String pathOf(String location) {
        return URI.create(location).getPath();
    }
}
