package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.security.oidc.ProviderHttp;
import com.projectmodule.security.oidc.Workeasy360Provider;
import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoFlowTestBase;
import com.projectmodule.security.support.TestClientKeys;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A server that sends its headers and then goes quiet. The JDK's per-request timeout stops at the headers, so
 * without an overall deadline such a server would hold a thread (and, before, the discovery lock) forever.
 * The application's HTTP timeout is 3 s in these tests; every case must finish well inside 3 deadlines.
 */
@DisplayName("A stalled Workeasy360 cannot hang the application")
class StalledServerTest extends SsoFlowTestBase {

    private static final long LIMIT_MILLIS = 8_000;

    private static long since(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    @Test
    @DisplayName("discovery that stalls after its headers -> unavailable, within the deadline")
    void stalledDiscovery() throws Exception {
        FAKE.stallDiscovery = true;
        long started = System.nanoTime();

        MvcResult result = mvc.perform(get("/api/auth/workeasy360/start")).andReturn();

        assertThat(location(result)).isEqualTo("/#/sign-in?sso_error=unavailable");
        assertThat(since(started)).isLessThan(LIMIT_MILLIS);
        assertThat(FAKE.stalledRequests).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a token endpoint that stalls -> unavailable, within the deadline")
    void stalledTokenEndpoint() throws Exception {
        FAKE.stallToken = true;
        long started = System.nanoTime();

        MvcResult result = signInFresh(null, null);

        assertThat(ssoError(result)).isEqualTo("unavailable");
        assertThat(since(started)).isLessThan(LIMIT_MILLIS);
    }

    @Test
    @DisplayName("a key set that stalls (an unknown kid forces the fetch) -> unavailable, within the deadline")
    void stalledKeySet() throws Exception {
        FAKE.stallJwks = true;
        long started = System.nanoTime();

        MvcResult result = signInFresh(null, spec -> spec.signing = "UNKNOWN_KID");

        assertThat(ssoError(result)).isEqualTo("unavailable");
        assertThat(since(started)).isLessThan(LIMIT_MILLIS);
    }

    @Test
    @DisplayName("an entitlement endpoint that stalls is an outage: access continues, within the deadline, once")
    void stalledEntitlementEndpoint() throws Exception {
        MockHttpSession session = sessionOf(signInFresh(null, spec -> spec.subject = UUID.randomUUID().toString()));
        FAKE.stallEntitlement = true;
        long started = System.nanoTime();

        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        long first = since(started);
        long second = System.nanoTime();
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());

        assertThat(first).isLessThan(LIMIT_MILLIS);
        assertThat(since(second)).as("the next attempt waits out the back-off").isLessThan(1_000);
        assertThat(FAKE.stalledRequests).isEqualTo(1);
    }

    @Test
    @DisplayName("the key set is never fetched through a redirect")
    void keySetRedirectIsNotFollowed() throws Exception {
        FAKE.jwksRedirects = true;

        MvcResult result = signInFresh(null, spec -> spec.signing = "UNKNOWN_KID");

        assertThat(ssoError(result)).isNotNull();
        assertThat(FAKE.followedRedirects).isZero();
    }

    // --- a stale document keeps serving while one thread refreshes ----------------------------------------------

    private static final class MovableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-12T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("while one thread refreshes a stale discovery document, every other thread keeps using the old one")
    void staleDocumentServesWhileRefreshing() throws Exception {
        Workeasy360Properties properties = new Workeasy360Properties(FAKE.issuer(), FakeWorkeasy360.CLIENT_ID,
                FakeWorkeasy360.REDIRECT_URI, TestClientKeys.RSA_KEY_ID, TestClientKeys.RSA_FILE.toString(), null,
                "easy_project", null, Duration.ofHours(1), Duration.ofSeconds(2));
        MovableClock clock = new MovableClock();
        Workeasy360Provider fresh = new Workeasy360Provider(properties,
                new ProviderHttp(properties.httpTimeout(), new ObjectMapper()), clock);
        String endpoint = fresh.registration().getProviderDetails().getTokenUri();
        clock.now = clock.now.plus(Duration.ofHours(2));
        FAKE.stallDiscovery = true;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> refresher = () -> fresh.registration().getProviderDetails().getTokenUri();
            Future<String> refreshing = pool.submit(refresher);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (FAKE.stalledRequests < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(FAKE.stalledRequests).as("the refresh is in flight and stalled").isEqualTo(1);

            long started = System.nanoTime();
            String meanwhile = fresh.registration().getProviderDetails().getTokenUri();
            long waited = since(started);

            assertThat(meanwhile).isEqualTo(endpoint);
            assertThat(waited).as("did not wait for the stalled refresh").isLessThan(500);
            assertThat(fresh.metadataString("workeasy360_entitlement_endpoint")).isNotNull();
            assertThat(refreshing.get(8, TimeUnit.SECONDS)).as("the refresher itself falls back to it").isEqualTo(endpoint);
            assertThat(FAKE.stalledRequests).as("one refresh, not one per caller").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the shared HTTP client enforces its deadline over the whole body")
    void deadlineCoversTheBody() throws Exception {
        ProviderHttp http = new ProviderHttp(Duration.ofSeconds(1), new ObjectMapper());
        FAKE.stallDiscovery = true;
        long started = System.nanoTime();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> http.get(FAKE.issuer() + "/.well-known/openid-configuration"))
                .isInstanceOf(IOException.class);

        assertThat(since(started)).isBetween(500L, 4_000L);
    }
}
