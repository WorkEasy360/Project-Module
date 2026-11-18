package com.projectmodule.security.backchannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL sign-in record that every instance shares ({@code V16}): what a logout token ends, the memory
 * of ended {@code sid}s, the clean-up of abandoned rows, that ending joins the {@code jti} transaction (so a
 * failure records nothing), and that a sign-in and a logout for the same {@code sid} on two instances at once
 * are serialised. Runs without the usual per-test rollback because the record opens its own transactions.
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, like every other persistence test here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JdbcSignInRecords.class, JdbcReceivedLogoutTokens.class, SignInRecordsPersistenceTest.Config.class})
@DisplayName("The shared sign-in record (PostgreSQL)")
class SignInRecordsPersistenceTest {

    private static final String ISSUER = "https://accounts.workeasy360.com";
    private static final String OTHER_ISSUER = "https://app.workeasy360.com";

    static final class MovableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-18T10:00:00Z");

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

    @TestConfiguration
    static class Config {
        @Bean
        MovableClock clock() {
            return new MovableClock();
        }
    }

    @Autowired
    private JdbcSignInRecords records;

    @Autowired
    private JdbcReceivedLogoutTokens ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MovableClock clock;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM signed_in_sessions");
        jdbc.update("DELETE FROM ended_sign_ins");
        jdbc.update("DELETE FROM received_logout_tokens");
    }

    private int rows() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM signed_in_sessions", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("a recorded session is open until it is closed; an unknown or malformed record id is not open")
    void openAndClosed() {
        String id = records.open(ISSUER, "pat", "sid-1");

        assertThat(id).isNotNull();
        assertThat(records.isOpen(id)).isTrue();
        assertThat(records.isOpen("6f1b2c3d-0000-4000-8000-000000000000")).isFalse();
        assertThat(records.isOpen("not-a-record-id")).isFalse();
        assertThat(records.isOpen(null)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM signed_in_sessions WHERE id::text = ?", Integer.class, id))
                .as("the row is keyed by the record id; no session id is stored anywhere").isEqualTo(1);

        records.closed(id);
        assertThat(records.isOpen(id)).isFalse();
        records.closed("not-a-record-id");
    }

    @Test
    @DisplayName("a sid ends that sign-in's sessions of that person, under that issuer, and nothing else")
    void sid() {
        String named = records.open(ISSUER, "pat", "sid-1");
        String sameSignInTwice = records.open(ISSUER, "pat", "sid-1");
        String otherSignIn = records.open(ISSUER, "pat", "sid-2");
        String otherIssuer = records.open(OTHER_ISSUER, "pat", "sid-1");
        String otherPerson = records.open(ISSUER, "sam", "sid-1");

        assertThat(records.end(ISSUER, "pat", "sid-1")).isEqualTo(2);

        assertThat(records.isOpen(named)).isFalse();
        assertThat(records.isOpen(sameSignInTwice)).isFalse();
        assertThat(records.isOpen(otherSignIn)).isTrue();
        assertThat(records.isOpen(otherIssuer)).isTrue();
        assertThat(records.isOpen(otherPerson)).as("sub given: only that person's").isTrue();
        assertThat(records.end(ISSUER, "pat", "sid-1")).as("already ended: nothing more, no error").isZero();
    }

    @Test
    @DisplayName("a sid without sub ends every session of that sign-in")
    void sidWithoutSubject() {
        String pat = records.open(ISSUER, "pat", "sid-1");
        String other = records.open(ISSUER, "pat", "sid-2");

        assertThat(records.end(ISSUER, null, "sid-1")).isEqualTo(1);

        assertThat(records.isOpen(pat)).isFalse();
        assertThat(records.isOpen(other)).isTrue();
    }

    @Test
    @DisplayName("only a sub ends every session of that person under that issuer, with or without a sid")
    void subjectOnly() {
        String a = records.open(ISSUER, "pat", "sid-1");
        String b = records.open(ISSUER, "pat", null);
        String otherIssuer = records.open(OTHER_ISSUER, "pat", null);
        String otherPerson = records.open(ISSUER, "sam", null);

        assertThat(records.end(ISSUER, "pat", null)).isEqualTo(2);

        assertThat(records.isOpen(a)).isFalse();
        assertThat(records.isOpen(b)).isFalse();
        assertThat(records.isOpen(otherIssuer)).isTrue();
        assertThat(records.isOpen(otherPerson)).isTrue();
    }

    @Test
    @DisplayName("a sign-in whose sid already ended cannot be recorded, on any instance, for ten minutes")
    void endedSignInIsRemembered() {
        assertThat(records.end(ISSUER, "pat", "sid-1")).isZero();

        assertThat(records.open(ISSUER, "pat", "sid-1")).isNull();
        assertThat(rows()).isZero();
        assertThat(records.open(OTHER_ISSUER, "pat", "sid-1")).as("another issuer's sid of the same value").isNotNull();
        assertThat(records.open(ISSUER, "pat", null)).as("no sid: nothing to match").isNotNull();

        clock.now = clock.now.plus(SignInSessions.ENDED_SIGN_IN_MEMORY).minusSeconds(1);
        assertThat(records.open(ISSUER, "pat", "sid-1")).isNull();
        clock.now = clock.now.plusSeconds(1);
        assertThat(records.open(ISSUER, "pat", "sid-1")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ended_sign_ins", Integer.class))
                .as("the expired memory was deleted by that sign-in").isZero();
    }

    @Test
    @DisplayName("a token naming only sub leaves no sid behind to refuse")
    void subjectOnlyRemembersNoSid() {
        records.end(ISSUER, "pat", null);

        assertThat(records.open(ISSUER, "pat", "sid-1")).isNotNull();
    }

    @Test
    @DisplayName("seen_at is refreshed at most every five minutes while the session is used")
    void seenAt() {
        String id = records.open(ISSUER, "pat", "sid-1");
        Instant opened = clock.now;

        clock.now = opened.plus(JdbcSignInRecords.SEEN_REFRESH).minusSeconds(1);
        assertThat(records.isOpen(id)).isTrue();
        assertThat(seenAt(id)).as("no write inside the interval").isEqualTo(opened);

        clock.now = opened.plus(JdbcSignInRecords.SEEN_REFRESH).plusSeconds(1);
        assertThat(records.isOpen(id)).isTrue();
        assertThat(seenAt(id)).isEqualTo(clock.now);
    }

    @Test
    @DisplayName("rows unseen for longer than the idle timeout plus the margin are deleted by the next sign-in")
    void abandonedRows() {
        Duration abandonedAfter = Duration.ofHours(8).plus(JdbcSignInRecords.SEEN_REFRESH)
                .plus(JdbcSignInRecords.ABANDONED_MARGIN);
        String abandoned = records.open(ISSUER, "pat", "sid-1");
        String used = records.open(ISSUER, "sam", "sid-2");

        clock.now = clock.now.plus(abandonedAfter).minus(Duration.ofMinutes(1));
        assertThat(records.isOpen(used)).isTrue();
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        records.open(ISSUER, "kim", "sid-3");

        assertThat(records.isOpen(abandoned)).isFalse();
        assertThat(records.isOpen(used)).as("still in use: seen within the window").isTrue();
    }

    @Test
    @DisplayName("ending joins the jti transaction: when ending the sessions fails, neither the jti nor the ending is recorded")
    void endingJoinsTheJtiTransaction() {
        String id = records.open(ISSUER, "pat", "sid-1");

        assertThatThrownBy(() -> ledger.acceptOnce(ISSUER, "jti-1", clock.now.plusSeconds(120), () -> {
            assertThat(records.end(ISSUER, "pat", "sid-1")).isEqualTo(1);
            throw new IllegalStateException("could not end a session here");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(records.isOpen(id)).as("rolled back with the jti").isTrue();
        assertThat(records.open(ISSUER, "pat", "sid-1")).as("the sid was not remembered either").isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM received_logout_tokens", Integer.class)).isZero();

        // The retry is processed afresh.
        assertThat(ledger.acceptOnce(ISSUER, "jti-1", clock.now.plusSeconds(120),
                () -> records.end(ISSUER, "pat", "sid-1"))).isTrue();
        assertThat(records.isOpen(id)).isFalse();
    }

    @Test
    @DisplayName("a sign-in waits for a logout of the same sid still in progress elsewhere, then is refused")
    void signInWaitsForTheLogout() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch ended = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> logout = pool.submit(() -> transaction.execute(status -> {
                int count = records.end(ISSUER, "pat", "sid-race");
                ended.countDown();
                await(commit);
                return count;
            }));
            assertThat(ended.await(10, TimeUnit.SECONDS)).isTrue();

            Future<String> signIn = pool.submit(() -> records.open(ISSUER, "pat", "sid-race"));
            assertThatThrownBy(() -> signIn.get(1, TimeUnit.SECONDS)).as("held until the logout commits")
                    .isInstanceOf(TimeoutException.class);

            commit.countDown();
            assertThat(logout.get(10, TimeUnit.SECONDS)).isZero();
            assertThat(signIn.get(10, TimeUnit.SECONDS)).as("then it finds the sid ended").isNull();
            assertThat(rows()).isZero();
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a logout waits for a sign-in of the same sid still in progress elsewhere, then ends its session")
    void logoutWaitsForTheSignIn() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> signIn = pool.submit(() -> transaction.execute(status -> {
                String id = records.open(ISSUER, "pat", "sid-race");
                opened.countDown();
                await(commit);
                return id;
            }));
            assertThat(opened.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> logout = pool.submit(() -> transaction.execute(
                    status -> records.end(ISSUER, "pat", "sid-race")));
            assertThatThrownBy(() -> logout.get(1, TimeUnit.SECONDS)).as("held until the sign-in commits")
                    .isInstanceOf(TimeoutException.class);

            commit.countDown();
            String id = signIn.get(10, TimeUnit.SECONDS);
            assertThat(id).isNotNull();
            assertThat(logout.get(10, TimeUnit.SECONDS)).as("then it sees, and ends, that session").isEqualTo(1);
            assertThat(records.isOpen(id)).isFalse();
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("sign-ins and logouts of the same sid racing on many threads: no session of an ended sid survives")
    void racingSignInsAndLogouts() throws Exception {
        int rounds = 40;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                String sid = "sid-" + round;
                CountDownLatch go = new CountDownLatch(1);
                Future<String> signIn = pool.submit(() -> {
                    await(go);
                    return records.open(ISSUER, "pat", sid);
                });
                Future<Integer> logout = pool.submit(() -> {
                    await(go);
                    return records.end(ISSUER, "pat", sid);
                });
                go.countDown();
                String id = signIn.get(10, TimeUnit.SECONDS);
                logout.get(10, TimeUnit.SECONDS);
                if (id != null) {
                    assertThat(records.isOpen(id)).as("round %d: a session recorded for an ended sid", round)
                            .isFalse();
                }
            }
            assertThat(rows()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    private Instant seenAt(String id) {
        List<java.time.OffsetDateTime> seen = jdbc.queryForList(
                "SELECT seen_at FROM signed_in_sessions WHERE id::text = ?", java.time.OffsetDateTime.class, id);
        return seen.get(0).toInstant();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
