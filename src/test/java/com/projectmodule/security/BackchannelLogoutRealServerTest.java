package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.dashboard.application.DashboardApplicationService;
import com.projectmodule.security.backchannel.SignInSessions;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoTestApplication;
import com.projectmodule.security.support.TestClientKeys;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Back-channel logout behind a real servlet container and over real HTTP: Workeasy360's POST ends the
 * container's own session (the browser's cookie stops working), the answer sets no cookie, and the container
 * reports sessions that end for other reasons to the sign-in index, so it does not grow.
 */
@SpringBootTest(classes = {SsoTestApplication.class, BackchannelLogoutRealServerTest.NotATestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Back-channel logout over real HTTP")
class BackchannelLogoutRealServerTest {

    /** Lets Spring Boot skip its search for the application class; see SsoFlowTestBase. */
    public static class NotATestConfiguration {
    }

    private static final FakeWorkeasy360 FAKE = FakeWorkeasy360.shared();
    private static final String SESSION_COOKIE = "__Host-ep_session";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("workeasy360.issuer", FAKE::issuer);
        registry.add("workeasy360.client-id", () -> FakeWorkeasy360.CLIENT_ID);
        registry.add("workeasy360.redirect-uri", () -> FakeWorkeasy360.REDIRECT_URI);
        registry.add("workeasy360.key-id", () -> TestClientKeys.RSA_KEY_ID);
        registry.add("workeasy360.private-key-file", () -> TestClientKeys.RSA_FILE.toString());
    }

    @MockitoBean
    private DashboardApplicationService dashboardService;

    @LocalServerPort
    private int port;

    @Autowired
    private SignInSessions signIns;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    @DisplayName("Workeasy360's POST ends the browser's session in the container; the answer is no-store and sets no cookie")
    void endsTheContainersSession() throws Exception {
        FAKE.reset();
        String person = UUID.randomUUID().toString();
        String sid = UUID.randomUUID().toString();
        String cookie = signIn(null, spec -> {
            spec.subject = person;
            spec.extra.put("sid", sid);
        });
        assertThat(get("/api/auth/session", cookie).statusCode()).isEqualTo(200);

        FakeWorkeasy360.LogoutTokenSpec spec = new FakeWorkeasy360.LogoutTokenSpec();
        spec.subject = person;
        spec.sid = sid;
        HttpResponse<String> answer = client.send(HttpRequest.newBuilder(uri(
                        "/api/auth/workeasy360/backchannel-logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("logout_token="
                        + URLEncoder.encode(FAKE.mintLogoutToken(spec), StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(answer.statusCode()).isEqualTo(200);
        assertThat(answer.headers().allValues("Cache-Control")).containsExactly("no-store");
        assertThat(answer.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(get("/api/auth/session", cookie).statusCode()).as("the cookie opens nothing any more")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("sessions that end otherwise (here: another sign-in in the same browser) leave the index")
    void theContainerReportsEndedSessions() throws Exception {
        FAKE.reset();
        String first = signIn(null, spec -> spec.subject = UUID.randomUUID().toString());
        int tracked = signIns.size();

        String second = signIn(first, spec -> spec.subject = UUID.randomUUID().toString());

        assertThat(second).isNotEqualTo(first);
        assertThat(get("/api/auth/session", first).statusCode()).isEqualTo(401);
        assertThat(signIns.size()).as("the replaced session was reported and forgotten").isEqualTo(tracked);
    }

    /** A complete sign-in over HTTP (optionally in a browser that already has a session); the new cookie. */
    private String signIn(String existingCookie, Consumer<FakeWorkeasy360.IdTokenSpec> customizer) throws Exception {
        HttpResponse<String> started = get("/api/auth/workeasy360/start", existingCookie);
        assertThat(started.statusCode()).isEqualTo(302);
        String flowCookie = existingCookie != null ? existingCookie
                : valueOf(cookie(started.headers().allValues("Set-Cookie")));
        FakeWorkeasy360.Authorization authorization = FAKE.parse(started.headers().firstValue("Location").orElseThrow());
        String code = FAKE.issueCode(authorization, customizer);

        HttpResponse<String> callback = get("/api/auth/callback/workeasy360?code=" + code + "&state="
                + authorization.state(), flowCookie);

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location")).contains("/");
        return valueOf(cookie(callback.headers().allValues("Set-Cookie")));
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String cookie(List<String> setCookies) {
        return setCookies.stream().filter(value -> value.startsWith(SESSION_COOKIE + "=")).findFirst().orElseThrow(
                () -> new AssertionError("no " + SESSION_COOKIE + " cookie in " + setCookies));
    }

    private static String valueOf(String setCookie) {
        Matcher matcher = Pattern.compile("^([^=]+=[^;]*)").matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
