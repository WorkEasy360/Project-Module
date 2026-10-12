package com.projectmodule.security.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.security.Workeasy360Principal;
import com.projectmodule.security.entitlement.EntitlementClient.Answer;
import com.projectmodule.security.entitlement.EntitlementService.Access;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The cadence and cache rules of the entitlement check, with a scripted provider and a clock we move. */
@DisplayName("Entitlement cadence")
class EntitlementServiceTest {

    /** A clock the test moves. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** What the scripted provider does next. */
    private interface Step {
        Answer answer() throws EntitlementClient.Unavailable, EntitlementClient.Refused;
    }

    private static final class ScriptedClient extends EntitlementClient {
        final List<Step> steps = new ArrayList<>();
        int asked;
        Runnable beforeAnswer = () -> { };

        ScriptedClient() {
            super(null);
        }

        @Override
        public Answer ask(UUID organizationId, String subject)
                throws EntitlementClient.Unavailable, EntitlementClient.Refused {
            asked++;
            beforeAnswer.run();
            if (steps.isEmpty()) {
                throw new EntitlementClient.Unavailable("nothing scripted");
            }
            return steps.size() > 1 ? steps.remove(0).answer() : steps.get(0).answer();
        }
    }

    private static Step allow(Integer revalidateAfter) {
        return () -> new Answer(true, true, "entitled", revalidateAfter);
    }

    private static Step readOnly() {
        return () -> new Answer(true, false, "read_only", 900);
    }

    private static Step deny(String reason) {
        return () -> new Answer(false, false, reason, 900);
    }

    private static Step outage() {
        return () -> {
            throw new EntitlementClient.Unavailable("503");
        };
    }

    private static Step refused() {
        return () -> {
            throw new EntitlementClient.Refused("401");
        };
    }

    private final MovableClock clock = new MovableClock();
    private final ScriptedClient client = new ScriptedClient();
    private EntitlementService service;
    private final Workeasy360Principal person = principal(UUID.randomUUID(), UUID.randomUUID().toString());

    private static Workeasy360Principal principal(UUID organization, String subject) {
        return new Workeasy360Principal(UUID.randomUUID(), organization, "https://issuer", subject, null, null, 1, null);
    }

    @BeforeEach
    void setUp() {
        service = new EntitlementService(client, clock, Duration.ofHours(8));
    }

    private Access ask() {
        return service.accessFor(person);
    }

    private void minutes(long minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    private void seconds(long seconds) {
        clock.advance(Duration.ofSeconds(seconds));
    }

    // --- allow ------------------------------------------------------------------------------------

    @Test
    @DisplayName("an allow is trusted for the advertised revalidate_after, then asked again")
    void allowIsTrustedForRevalidateAfter() {
        client.steps.add(allow(120));

        assertThat(ask().allowed()).isTrue();
        seconds(119);
        ask();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        ask();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("an allow is trusted for at most 900 s however long the provider says")
    void allowIsCappedAtFifteenMinutes() {
        client.steps.add(allow(86_400));

        ask();
        seconds(899);
        ask();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        ask();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("an allow is trusted for at least 30 s, and for 900 s when no cadence is advertised")
    void allowHasAFloorAndADefault() {
        client.steps.add(allow(1));
        ask();
        seconds(29);
        ask();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        ask();
        assertThat(client.asked).isEqualTo(2);

        EntitlementService other = new EntitlementService(client, clock, Duration.ofHours(8));
        client.asked = 0;
        client.steps.clear();
        client.steps.add(allow(null));
        other.accessFor(person);
        seconds(899);
        other.accessFor(person);
        assertThat(client.asked).isEqualTo(1);
    }

    @Test
    @DisplayName("read-only is relayed: allowed, not writable")
    void readOnlyIsRelayed() {
        client.steps.add(readOnly());

        Access access = ask();

        assertThat(access.allowed()).isTrue();
        assertThat(access.writeAllowed()).isFalse();
    }

    // --- denial ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a denial is asked again after 60 s, so a renewal takes effect quickly")
    void denialIsReAskedAfterAMinute() {
        client.steps.add(deny("expired"));
        client.steps.add(allow(900));

        assertThat(ask().allowed()).isFalse();
        seconds(59);
        assertThat(ask().allowed()).isFalse();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        assertThat(ask().allowed()).isTrue();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("the reason of a denial is kept")
    void denialKeepsItsReason() {
        client.steps.add(deny("suspended"));

        assertThat(ask().reason()).isEqualTo("suspended");
    }

    // --- outage ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an outage with no answer yet lets access continue, and is not repeated for 60 s")
    void outageWithNoAnswerContinues() {
        client.steps.add(outage());

        Access first = ask();

        assertThat(first.allowed()).isTrue();
        assertThat(first.writeAllowed()).isTrue();
        assertThat(first.reason()).isEqualTo("unchecked");
        seconds(59);
        ask();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        ask();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("an outage keeps the last answer: an allow stands past its window")
    void outageKeepsTheLastAllow() {
        client.steps.add(allow(60));
        client.steps.add(outage());
        ask();

        minutes(30);
        Access during = ask();

        assertThat(during.allowed()).isTrue();
        assertThat(during.reason()).isEqualTo("entitled");
    }

    @Test
    @DisplayName("an outage keeps the last answer: a denial stands, however long the outage lasts")
    void aKnownDenialNeverLapsesIntoAccess() {
        client.steps.add(deny("expired"));
        client.steps.add(outage());
        assertThat(ask().allowed()).isFalse();

        for (int hour = 0; hour < 72; hour++) {
            minutes(60);
            assertThat(ask().allowed()).as("hour %d", hour).isFalse();
        }
    }

    @Test
    @DisplayName("after the outage ends the next answer replaces the old one")
    void recoveryReplacesTheAnswer() {
        client.steps.add(deny("expired"));
        client.steps.add(outage());
        client.steps.add(allow(900));
        ask();
        minutes(2);
        assertThat(ask().allowed()).isFalse(); // outage: the denial stands
        minutes(2);
        assertThat(ask().allowed()).isTrue();
    }

    // --- refusal -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Workeasy360 refusing this client (400/401/403) is not an outage: it fails closed and is re-asked each minute")
    void refusalFailsClosed() {
        client.steps.add(refused());
        client.steps.add(allow(900));

        Access first = ask();

        assertThat(first.allowed()).isFalse();
        assertThat(first.refused()).isTrue();
        seconds(59);
        assertThat(ask().refused()).isTrue();
        assertThat(client.asked).isEqualTo(1);
        seconds(2);
        assertThat(ask().allowed()).isTrue();
    }

    @Test
    @DisplayName("a refusal replaces an earlier allow: the product does not keep serving on a revoked credential")
    void refusalReplacesAnAllow() {
        client.steps.add(allow(30));
        client.steps.add(refused());
        ask();

        seconds(31);

        assertThat(ask().refused()).isTrue();
    }

    // --- single flight -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("while one request asks, the others use the last answer")
    void oneAsksTheOthersUseTheLastAnswer() throws Exception {
        client.steps.add(allow(30));
        ask();
        seconds(31);
        client.steps.clear();
        client.steps.add(deny("suspended"));
        CountDownLatch asking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        client.beforeAnswer = () -> {
            asking.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Access> asker = pool.submit(this::ask);
            assertThat(asking.await(5, TimeUnit.SECONDS)).isTrue();

            Access meanwhile = ask(); // another request, while the first is in flight

            assertThat(meanwhile.allowed()).as("the last answer").isTrue();
            assertThat(client.asked).isEqualTo(2); // the earlier one and the one in flight
            release.countDown();
            assertThat(asker.get(5, TimeUnit.SECONDS).allowed()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a first check is never waved through because another request is asking")
    void aFirstCheckIsNeverSkipped() throws Exception {
        client.steps.add(deny("expired"));
        CountDownLatch asking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        client.beforeAnswer = () -> {
            asking.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Access> first = pool.submit(this::ask);
            assertThat(asking.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Access> second = pool.submit(this::ask);
            // Nobody has an answer yet, so the second request asks too instead of assuming access.
            Thread.sleep(200);
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).allowed()).isFalse();
            assertThat(second.get(5, TimeUnit.SECONDS).allowed()).isFalse();
            assertThat(client.asked).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- keys and bounds ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("answers are per (organization, person)")
    void answersArePerOrganizationAndPerson() {
        client.steps.add(allow(900));
        UUID organization = UUID.randomUUID();
        service.accessFor(principal(organization, "a"));
        service.accessFor(principal(organization, "a"));
        service.accessFor(principal(organization, "b"));
        service.accessFor(principal(UUID.randomUUID(), "a"));

        assertThat(client.asked).isEqualTo(3);
    }

    @Test
    @DisplayName("a flood of denials cannot push a fresh allowance out of the cache, and is itself bounded")
    void denialsDoNotEvictAllowances() {
        client.steps.add(allow(900));
        Workeasy360Principal kept = principal(UUID.randomUUID(), "kept");
        service.accessFor(kept);
        client.steps.clear();
        client.steps.add(deny("expired"));
        for (int i = 0; i < EntitlementService.MAX_ENTRIES + 50; i++) {
            service.accessFor(principal(UUID.randomUUID(), "denied-" + i));
        }
        int asked = client.asked;

        assertThat(service.accessFor(kept).allowed()).isTrue();

        assertThat(client.asked).as("still cached, not asked again").isEqualTo(asked);
        assertThat(service.allowanceCount()).isEqualTo(1);
        assertThat(service.denialCount()).isLessThanOrEqualTo(EntitlementService.MAX_ENTRIES);
    }

    @Test
    @DisplayName("a flood of allowances cannot push a denial out of the cache")
    void allowancesDoNotEvictDenials() {
        client.steps.add(deny("expired"));
        Workeasy360Principal denied = principal(UUID.randomUUID(), "denied-person");
        service.accessFor(denied);
        client.steps.clear();
        client.steps.add(allow(900));
        for (int i = 0; i < EntitlementService.MAX_ENTRIES + 50; i++) {
            service.accessFor(principal(UUID.randomUUID(), "person-" + i));
        }
        assertThat(service.allowanceCount()).isLessThanOrEqualTo(EntitlementService.MAX_ENTRIES);
        client.steps.clear();
        client.steps.add(outage());
        minutes(5); // past its 60 s window, so the provider is asked (and fails)

        assertThat(service.accessFor(denied).allowed()).as("the known denial survived").isFalse();
    }

    @Test
    @DisplayName("a denial nobody has looked at for longer than the session idle timeout is forgotten")
    void idleDenialIsForgotten() {
        client.steps.add(deny("expired"));
        ask();
        client.steps.clear();
        client.steps.add(outage());

        minutes(9 * 60);

        Access afterwards = ask();
        assertThat(afterwards.allowed()).as("forgotten: with no answer, an outage lets access continue").isTrue();
        assertThat(afterwards.reason()).isEqualTo("unchecked");
        assertThat(service.denialCount()).isZero();
    }

    @Test
    @DisplayName("a denial that keeps being used never expires")
    void usedDenialStays() {
        client.steps.add(deny("expired"));
        client.steps.add(outage());
        ask();

        for (int visit = 0; visit < 12; visit++) {
            minutes(7 * 60); // seven hours apart: each visit refreshes the idle clock
            assertThat(ask().allowed()).as("visit %d", visit).isFalse();
        }
    }

    @Test
    @DisplayName("idle denials are dropped from memory when a new denial is stored")
    void idleDenialsAreDroppedOnInsert() {
        client.steps.add(deny("expired"));
        service.accessFor(principal(UUID.randomUUID(), "a"));
        service.accessFor(principal(UUID.randomUUID(), "b"));
        assertThat(service.denialCount()).isEqualTo(2);

        minutes(9 * 60);
        service.accessFor(principal(UUID.randomUUID(), "c"));

        assertThat(service.denialCount()).isEqualTo(1);
    }

    // --- a refusal of the product's own client is client-wide -----------------------------------------------

    private static Step rateLimited() {
        return () -> {
            throw new EntitlementClient.RateLimited("429");
        };
    }

    private Workeasy360Principal somebodyElse() {
        return principal(UUID.randomUUID(), "somebody-else");
    }

    @Test
    @DisplayName("one refusal fails every person closed for 60 s without asking again")
    void refusalIsClientWide() {
        client.steps.add(refused());
        assertThat(ask().refused()).isTrue();

        for (int i = 0; i < 25; i++) {
            Access other = service.accessFor(somebodyElse());
            assertThat(other.allowed()).isFalse();
            assertThat(other.refused()).isTrue();
        }

        assertThat(client.asked).as("nobody else was asked").isEqualTo(1);
    }

    @Test
    @DisplayName("a refusal also closes people whose allowance is still fresh")
    void refusalClosesCachedAllowances() {
        Workeasy360Principal cached = somebodyElse();
        client.steps.add(allow(900));
        client.steps.add(refused());
        assertThat(service.accessFor(cached).allowed()).isTrue();
        assertThat(ask().refused()).isTrue();

        assertThat(service.accessFor(cached).refused()).isTrue();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("after the 60 s exactly one question is asked; everyone else stays closed until it is answered")
    void oneProbeAfterTheWindow() throws Exception {
        client.steps.add(refused());
        ask();
        seconds(61);
        client.steps.clear();
        client.steps.add(allow(900));
        CountDownLatch asking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        client.beforeAnswer = () -> {
            asking.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Access> probe = pool.submit(() -> service.accessFor(somebodyElse()));
            assertThat(asking.await(5, TimeUnit.SECONDS)).isTrue();

            Access meanwhile = service.accessFor(somebodyElse());

            assertThat(meanwhile.refused()).as("still closed while the probe is out").isTrue();
            assertThat(client.asked).isEqualTo(2); // the refusal and the probe
            release.countDown();
            assertThat(probe.get(5, TimeUnit.SECONDS).allowed()).isTrue();
        } finally {
            pool.shutdownNow();
        }
        client.beforeAnswer = () -> { };
        assertThat(service.accessFor(somebodyElse()).allowed()).as("reopened for everybody").isTrue();
    }

    @Test
    @DisplayName("a refusal that still stands after the 60 s closes everybody for another 60 s")
    void refusalThatStandsRenews() {
        client.steps.add(refused());
        ask();
        seconds(61);

        assertThat(service.accessFor(somebodyElse()).refused()).isTrue(); // the probe: refused again
        seconds(30);
        assertThat(service.accessFor(somebodyElse()).refused()).isTrue();
        assertThat(client.asked).as("the refusal and one probe").isEqualTo(2);
    }

    @Test
    @DisplayName("a 429 from the failure limiter right after a refusal is the refusal, not an outage")
    void rateLimitAfterARefusalIsARefusal() {
        client.steps.add(refused());
        client.steps.add(rateLimited());
        ask();
        seconds(61);

        Access probe = service.accessFor(somebodyElse());

        assertThat(probe.refused()).isTrue();
        seconds(30);
        assertThat(service.accessFor(somebodyElse()).refused()).isTrue();
        assertThat(client.asked).isEqualTo(2);
    }

    @Test
    @DisplayName("a 429 with no refusal before it is an outage: access continues")
    void rateLimitAloneIsAnOutage() {
        client.steps.add(rateLimited());

        Access access = ask();

        assertThat(access.allowed()).isTrue();
        assertThat(access.reason()).isEqualTo("unchecked");
    }

    @Test
    @DisplayName("once the refusal is over and long past, a 429 is an outage again")
    void rateLimitLongAfterIsAnOutage() {
        client.steps.add(refused());
        client.steps.add(allow(30));
        client.steps.add(rateLimited());
        ask();
        seconds(61);
        service.accessFor(somebodyElse()); // the probe: the refusal is over
        minutes(10);

        Access access = ask();

        assertThat(access.refused()).isFalse();
    }
}
