package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.dashboard.application.DashboardApplicationService;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoTestApplication;
import com.projectmodule.security.support.TestClientKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same security chain behind a real servlet container and a real HTTP client: what MockMvc cannot
 * show &mdash; the actual {@code Set-Cookie} attributes, the security headers, the configured idle timeout &mdash;
 * and one complete sign-in over HTTP.
 */
@SpringBootTest(classes = {SsoTestApplication.class, RealServerSignInTest.NotATestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Cookies and headers over real HTTP")
class RealServerSignInTest {

    /** Lets Spring Boot skip its search for the application class; see SsoFlowTestBase. */
    public static class NotATestConfiguration {
    }

    private static final FakeWorkeasy360 FAKE = FakeWorkeasy360.shared();

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
    private ServerProperties serverProperties;

    @Value("${server.servlet.session.cookie.secure}")
    private boolean cookieSecure;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> send(String path, String cookies) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(List<String> setCookies, String name) {
        return setCookies.stream().filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow(
                () -> new AssertionError("no " + name + " cookie in " + setCookies));
    }

    private static String valueOf(String setCookie) {
        Matcher matcher = Pattern.compile("^([^=]+=[^;]*)").matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("the session cookie is HttpOnly, SameSite=Lax and Secure; the idle timeout is 8 hours by default")
    void sessionCookieAttributes() throws Exception {
        HttpResponse<String> started = send("/api/auth/workeasy360/start", null);

        assertThat(started.statusCode()).isEqualTo(302);
        String sessionCookie = cookie(started.headers().allValues("Set-Cookie"), "__Host-ep_session");
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Secure").contains("Path=/")
                .doesNotContain("Domain");
        assertThat(sessionCookie).as("the __Host- prefix: a sibling subdomain cannot plant or replace it")
                .startsWith("__Host-ep_session=");
        assertThat(cookieSecure).as("secure unless the local profile turns it off").isTrue();
        assertThat(serverProperties.getServlet().getSession().getTimeout()).hasHours(8);
    }

    @Test
    @DisplayName("the CSRF cookie is readable by the app, SameSite=Lax and Secure; responses carry the security headers")
    void csrfCookieAndHeaders() throws Exception {
        HttpResponse<String> response = send("/api/auth/session", null);

        String csrf = cookie(response.headers().allValues("Set-Cookie"), "__Host-XSRF-TOKEN");
        assertThat(csrf).contains("SameSite=Lax").contains("Secure").contains("Path=/")
                .doesNotContain("HttpOnly").doesNotContain("Domain");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("same-origin");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
    }

    @Test
    @DisplayName("a complete sign-in over HTTP: the session cookie changes id and works; a header cannot stand in for it")
    void completeSignInOverHttp() throws Exception {
        String person = UUID.randomUUID().toString();
        HttpResponse<String> started = send("/api/auth/workeasy360/start", null);
        String before = valueOf(cookie(started.headers().allValues("Set-Cookie"), "__Host-ep_session"));
        FakeWorkeasy360.Authorization authorization = FAKE.parse(started.headers().firstValue("Location").orElseThrow());
        String code = FAKE.issueCode(authorization, spec -> spec.subject = person);

        HttpResponse<String> callback = send("/api/auth/callback/workeasy360?code=" + code + "&state="
                + authorization.state(), before);

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location")).contains("/");
        String after = valueOf(cookie(callback.headers().allValues("Set-Cookie"), "__Host-ep_session"));
        assertThat(after).isNotEqualTo(before);

        assertThat(send("/api/auth/session", after).statusCode()).isEqualTo(200);
        assertThat(send("/api/auth/session", before).statusCode()).as("the old id is dead").isEqualTo(401);

        HttpRequest spoofed = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/dashboard"))
                .header("X-User-Id", person).header("X-Org-Id", UUID.randomUUID().toString()).GET().build();
        assertThat(client.send(spoofed, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
}
