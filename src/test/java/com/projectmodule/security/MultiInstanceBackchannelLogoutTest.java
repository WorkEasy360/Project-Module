package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.ProjectModuleApplication;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.FakeWorkeasy360.LogoutTokenSpec;
import com.projectmodule.security.support.TestClientKeys;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Back-channel logout with more than one instance: two copies of the whole application (two embedded Tomcats,
 * each with its own in-memory sessions) sharing one PostgreSQL database, as behind a load balancer. People sign
 * in on one instance; Workeasy360's logout token is delivered to the other; the session cookie must stop working
 * where the session lives. Instance A persists its sessions across a restart (Tomcat's session store), so the
 * restart test brings a revoked session back from disk and proves it is still refused. Every request is real
 * HTTP with the browser's cookie; nothing is mocked except Workeasy360 ({@link FakeWorkeasy360}).
 *
 * <p>Easy Project's sessions are not shared between instances: a cookie from A means nothing to B, which answers
 * 401 for it whether or not the sign-in was ended. What is shared is the sign-in record, and that is what these
 * tests prove reaches every instance.
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, like every other real-database test.
 */
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Back-channel logout across instances: two servers, one database")
class MultiInstanceBackchannelLogoutTest {

    private static final FakeWorkeasy360 FAKE = FakeWorkeasy360.shared();
    private static final String SESSION_COOKIE = "__Host-ep_session";

    /** One running copy of the application. */
    private record Instance(String name, ConfigurableApplicationContext context, int port) {

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + port + path);
        }

        JdbcTemplate jdbc() {
            return context.getBean(JdbcTemplate.class);
        }
    }

    /**
     * The real application's component scan, without the test classes that share its packages (the test
     * framework's own filter does this for {@code @SpringBootTest}; these contexts are started by hand).
     */
    static final class ApplicationClassesOnly extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
            return reader.getResource().getURL().toString().contains("/test-classes/");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ApplicationClassesOnly;
        }

        @Override
        public int hashCode() {
            return ApplicationClassesOnly.class.hashCode();
        }
    }

    /** Where instance A's Tomcat keeps its sessions across a restart. Made and removed by this class. */
    private Path sessionStoreOfA;

    private Instance a;
    private Instance b;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeAll
    void startTwoInstances() throws IOException {
        FAKE.reset();
        sessionStoreOfA = Files.createTempDirectory("easy-project-sessions-a");
        a = start("instance-a", sessionStoreOfA);
        b = start("instance-b", null);
        assertThat(a.port()).isNotEqualTo(b.port());
    }

    @AfterAll
    void stopAndClean() {
        try {
            if (b != null) {
                JdbcTemplate jdbc = b.jdbc();
                for (String table : List.of("signed_in_sessions", "ended_sign_ins", "received_logout_tokens",
                        "federated_identities")) {
                    jdbc.update("DELETE FROM " + table + " WHERE issuer = ?", FAKE.issuer());
                }
            }
        } finally {
            if (a != null) {
                a.context().close();
            }
            if (b != null) {
                b.context().close();
            }
            deleteQuietly(sessionStoreOfA);
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var files = Files.walk(directory)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A temporary directory; the operating system cleans up what is left.
        }
    }

    private static Instance start(String name, Path sessionStore) {
        List<String> arguments = new java.util.ArrayList<>(List.of(
                "--server.port=0",
                "--workeasy360.issuer=" + FAKE.issuer(),
                "--workeasy360.client-id=" + FakeWorkeasy360.CLIENT_ID,
                "--workeasy360.redirect-uri=" + FakeWorkeasy360.REDIRECT_URI,
                "--workeasy360.key-id=" + TestClientKeys.RSA_KEY_ID,
                "--workeasy360.private-key-file=" + TestClientKeys.RSA_FILE,
                "--workeasy360.http-timeout=3s"));
        if (sessionStore != null) {
            arguments.add("--server.servlet.session.persistent=true");
            arguments.add("--server.servlet.session.store-dir=" + sessionStore);
        }
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ProjectModuleApplication.class)
                .profiles("test")
                .initializers(applicationContext -> applicationContext.getBeanFactory()
                        .registerSingleton(ApplicationClassesOnly.class.getName(), new ApplicationClassesOnly()))
                .run(arguments.toArray(String[]::new));
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new Instance(name, context, port);
    }

    // --- the tests ---------------------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("a sid logout delivered to B ends that sign-in's sessions on A (and on B), and nothing else")
    void sidLogoutDeliveredToTheOtherInstance() throws Exception {
        String person = uuid();
        String sid = uuid();
        String onA = signIn(a, person, sid);
        String sameSignInOnB = signIn(b, person, sid);
        String otherSignInOnA = signIn(a, person, uuid());
        String colleagueOnA = signIn(a, uuid(), uuid());
        assertThat(status(a, "/api/v1/dashboard", onA)).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", onA)).isEqualTo(200);
        assertThat(status(b, "/api/auth/session", sameSignInOnB)).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", otherSignInOnA)).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", colleagueOnA)).isEqualTo(200);

        HttpResponse<String> delivered = deliver(b, token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        }));

        assertThat(delivered.statusCode()).isEqualTo(200);
        assertThat(delivered.body()).isEmpty();
        assertThat(status(a, "/api/v1/dashboard", onA)).as("A's session, ended by B's delivery: the API")
                .isEqualTo(401);
        assertThat(status(a, "/api/auth/session", onA)).as("and the session read").isEqualTo(401);
        assertThat(status(b, "/api/auth/session", onA)).as("B never held A's session").isEqualTo(401);
        assertThat(status(b, "/api/auth/session", sameSignInOnB)).as("B's own session of that sign-in")
                .isEqualTo(401);
        assertThat(status(a, "/api/auth/session", otherSignInOnA)).as("the person's other sign-in").isEqualTo(200);
        assertThat(status(a, "/api/auth/session", colleagueOnA)).isEqualTo(200);
        assertThat(a.jdbc().queryForObject("SELECT count(*) FROM signed_in_sessions WHERE issuer = ? AND sid = ?",
                Integer.class, FAKE.issuer(), sid)).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("a sub-only logout delivered to A ends every session of that person on B (and on A)")
    void subjectLogoutDeliveredToTheOtherInstance() throws Exception {
        String person = uuid();
        String onB = signIn(b, person, uuid());
        String onBWithoutSid = signIn(b, person, null);
        String onA = signIn(a, person, uuid());
        String colleagueOnB = signIn(b, uuid(), uuid());

        assertThat(deliver(a, token(spec -> spec.subject = person)).statusCode()).isEqualTo(200);

        assertThat(status(b, "/api/auth/session", onB)).isEqualTo(401);
        assertThat(status(b, "/api/v1/dashboard", onBWithoutSid)).isEqualTo(401);
        assertThat(status(a, "/api/auth/session", onA)).isEqualTo(401);
        assertThat(status(b, "/api/auth/session", colleagueOnB)).isEqualTo(200);
    }

    @Test
    @Order(3)
    @DisplayName("the same token delivered again, to either instance or to both at once, gets the same 200 and ends nothing more")
    void duplicateDeliveries() throws Exception {
        String person = uuid();
        String first = signIn(a, person, uuid());
        String jti = "duplicate-" + uuid();
        String logoutToken = token(spec -> {
            spec.subject = person;
            spec.jti = jti;
        });

        assertThat(deliver(b, logoutToken).statusCode()).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", first)).isEqualTo(401);

        // The person signs in again; copies of the old token keep arriving (a retry whose answer was lost, a
        // proxy repeating the POST, a captured token).
        String later = signIn(a, person, uuid());
        HttpResponse<String> again = deliver(b, logoutToken);
        HttpResponse<String> elsewhere = deliver(a, logoutToken);

        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(again.body()).isEmpty();
        assertThat(elsewhere.statusCode()).isEqualTo(200);
        assertThat(elsewhere.body()).isEmpty();
        assertThat(status(a, "/api/auth/session", later)).as("acted on once: the new session survives")
                .isEqualTo(200);

        // One new token delivered to both instances at the same moment, as a load balancer retrying might.
        String concurrentJti = "concurrent-" + uuid();
        String concurrentToken = token(spec -> {
            spec.subject = person;
            spec.jti = concurrentJti;
        });
        CompletableFuture<HttpResponse<String>> toA = deliverAsync(a, concurrentToken);
        CompletableFuture<HttpResponse<String>> toB = deliverAsync(b, concurrentToken);
        assertThat(toA.join().statusCode()).isEqualTo(200);
        assertThat(toB.join().statusCode()).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", later)).isEqualTo(401);
        for (String recorded : List.of(jti, concurrentJti)) {
            assertThat(a.jdbc().queryForObject(
                    "SELECT count(*) FROM received_logout_tokens WHERE issuer = ? AND jti = ?",
                    Integer.class, FAKE.issuer(), recorded)).as("%s recorded once", recorded).isEqualTo(1);
        }
    }

    @Test
    @Order(4)
    @DisplayName("a logout delivered to B that overtakes a sign-in returning to A: the callback on A is refused (expired)")
    void logoutOvertakesTheCallbackAcrossInstances() throws Exception {
        String person = uuid();
        String sid = uuid();
        HttpResponse<String> started = get(a, "/api/auth/workeasy360/start", null);
        assertThat(started.statusCode()).isEqualTo(302);
        String flowCookie = cookieValue(started);
        FakeWorkeasy360.Authorization authorization = FAKE.parse(started.headers().firstValue("Location").orElseThrow());
        String code = FAKE.issueCode(authorization, spec -> {
            spec.subject = person;
            spec.extra.put("sid", sid);
        });

        assertThat(deliver(b, token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).statusCode()).isEqualTo(200);
        HttpResponse<String> callback = get(a, "/api/auth/callback/workeasy360?code=" + encode(code) + "&state="
                + encode(authorization.state()), flowCookie);

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location")).contains("/#/sign-in?sso_error=expired");
        String left = sessionCookieOrNull(callback);
        if (left != null) {
            assertThat(status(a, "/api/auth/session", left)).isEqualTo(401);
        }
        assertThat(status(a, "/api/auth/session", flowCookie)).isEqualTo(401);
    }

    @Test
    @Order(5)
    @DisplayName("a restarted instance brings its sessions back from disk, but not one whose sign-in was ended meanwhile")
    void restartDoesNotResurrectAnEndedSession() throws Exception {
        String control = signIn(a, uuid(), uuid());
        String person = uuid();
        String sid = uuid();
        String ended = signIn(a, person, sid);
        assertThat(status(a, "/api/auth/session", ended)).isEqualTo(200);
        assertThat(status(a, "/api/auth/session", control)).isEqualTo(200);

        // Ended through B while A holds the session; A sees no request for it before it restarts.
        assertThat(deliver(b, token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).statusCode()).isEqualTo(200);

        a.context().close();
        assertThat(Files.exists(sessionStoreOfA.resolve("SESSIONS.ser")))
                .as("A wrote its sessions to disk on the way down, the ended one included").isTrue();
        a = start("instance-a-restarted", sessionStoreOfA);

        assertThat(status(a, "/api/auth/session", control))
                .as("control: a session that was not ended survives the restart, so sessions really came back")
                .isEqualTo(200);
        assertThat(status(a, "/api/v1/dashboard", ended)).as("the ended one does not").isEqualTo(401);
        assertThat(status(a, "/api/auth/session", ended)).isEqualTo(401);
        assertThat(status(b, "/api/auth/session", ended)).isEqualTo(401);

        // And the restarted instance takes part like any other.
        assertThat(deliver(a, token(spec -> spec.subject = person)).statusCode()).isEqualTo(200);
    }

    // --- helpers -----------------------------------------------------------------------------------------

    /** A complete sign-in over HTTP on {@code at}; the new session cookie ({@code name=value}). */
    private String signIn(Instance at, String subject, String sid) throws Exception {
        HttpResponse<String> started = get(at, "/api/auth/workeasy360/start", null);
        assertThat(started.statusCode()).isEqualTo(302);
        String flowCookie = cookieValue(started);
        FakeWorkeasy360.Authorization authorization = FAKE.parse(started.headers().firstValue("Location").orElseThrow());
        String code = FAKE.issueCode(authorization, spec -> {
            spec.subject = subject;
            if (sid != null) {
                spec.extra.put("sid", sid);
            }
        });

        HttpResponse<String> callback = get(at, "/api/auth/callback/workeasy360?code=" + encode(code) + "&state="
                + encode(authorization.state()), flowCookie);

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location").orElseThrow()).doesNotContain("sso_error");
        return cookieValue(callback);
    }

    private static String token(Consumer<LogoutTokenSpec> customizer) throws Exception {
        LogoutTokenSpec spec = new LogoutTokenSpec();
        customizer.accept(spec);
        return FAKE.mintLogoutToken(spec);
    }

    /** As Workeasy360 delivers it: a form POST with no cookie. */
    private HttpResponse<String> deliver(Instance to, String logoutToken) throws Exception {
        return client.send(logoutRequest(to, logoutToken), HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> deliverAsync(Instance to, String logoutToken) {
        return client.sendAsync(logoutRequest(to, logoutToken), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest logoutRequest(Instance to, String logoutToken) {
        return HttpRequest.newBuilder(to.uri("/api/auth/workeasy360/backchannel-logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("logout_token=" + encode(logoutToken)))
                .build();
    }

    private int status(Instance at, String path, String cookie) throws Exception {
        return get(at, path, cookie).statusCode();
    }

    private HttpResponse<String> get(Instance at, String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(at.uri(path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieValue(HttpResponse<String> response) {
        String cookie = sessionCookieOrNull(response);
        if (cookie == null) {
            throw new AssertionError("no " + SESSION_COOKIE + " cookie in " + response.headers().allValues("Set-Cookie"));
        }
        return cookie;
    }

    private static String sessionCookieOrNull(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(SESSION_COOKIE + "="))
                .findFirst()
                .map(value -> {
                    Matcher matcher = Pattern.compile("^([^=]+=[^;]*)").matcher(value);
                    return matcher.find() ? matcher.group(1) : null;
                })
                .orElse(null);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
