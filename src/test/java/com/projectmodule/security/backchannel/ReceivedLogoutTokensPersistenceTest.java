package com.projectmodule.security.backchannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The PostgreSQL-backed ledger of accepted logout tokens: the {@code V23} migration, once-only acceptance,
 * nothing recorded when ending the sessions fails, expiry, and two deliveries of one token at once. Runs
 * without the usual per-test rollback because the ledger opens its own transactions.
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, like every other persistence test here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JdbcReceivedLogoutTokens.class, ReceivedLogoutTokensPersistenceTest.Config.class})
class ReceivedLogoutTokensPersistenceTest {

    private static final String ISSUER = "https://accounts.workeasy360.com";

    static final class MovableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-17T10:00:00Z");

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
    private JdbcReceivedLogoutTokens ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MovableClock clock;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM received_logout_tokens");
    }

    private Instant inTwoMinutes() {
        return clock.now.plus(Duration.ofMinutes(2));
    }

    private int rows() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM received_logout_tokens", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("a jti is accepted once: the second time nothing runs")
    void onceOnly() {
        AtomicInteger runs = new AtomicInteger();

        assertThat(ledger.acceptOnce(ISSUER, "jti-1", inTwoMinutes(), runs::incrementAndGet)).isTrue();
        assertThat(ledger.acceptOnce(ISSUER, "jti-1", inTwoMinutes(), runs::incrementAndGet)).isFalse();

        assertThat(runs).hasValue(1);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same jti from another issuer is another token")
    void perIssuer() {
        assertThat(ledger.acceptOnce(ISSUER, "jti-1", inTwoMinutes(), () -> { })).isTrue();
        assertThat(ledger.acceptOnce("https://app.workeasy360.com", "jti-1", inTwoMinutes(), () -> { })).isTrue();
    }

    @Test
    @DisplayName("when ending the sessions fails, the jti is not recorded and the retry is accepted")
    void failureRecordsNothing() {
        assertThatThrownBy(() -> ledger.acceptOnce(ISSUER, "jti-1", inTwoMinutes(), () -> {
            throw new IllegalStateException("could not end a session");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(rows()).isZero();

        AtomicInteger runs = new AtomicInteger();
        assertThat(ledger.acceptOnce(ISSUER, "jti-1", inTwoMinutes(), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("records past keep_until are deleted by the next accepted token; younger ones are kept")
    void expiry() {
        ledger.acceptOnce(ISSUER, "old", clock.now.plusSeconds(60), () -> { });
        ledger.acceptOnce(ISSUER, "young", clock.now.plusSeconds(600), () -> { });

        clock.now = clock.now.plusSeconds(120);
        ledger.acceptOnce(ISSUER, "new", inTwoMinutes(), () -> { });

        assertThat(jdbc.queryForList("SELECT jti FROM received_logout_tokens ORDER BY jti", String.class))
                .containsExactly("new", "young");
    }

    @Test
    @DisplayName("two deliveries of one token at once: exactly one is accepted")
    void concurrentDeliveries() throws Exception {
        int deliveries = 8;
        ExecutorService pool = Executors.newFixedThreadPool(deliveries);
        CountDownLatch ready = new CountDownLatch(deliveries);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < deliveries; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return ledger.acceptOnce(ISSUER, "jti-race", inTwoMinutes(), runs::incrementAndGet);
                }));
            }
            ready.await();
            go.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                accepted += result.get() ? 1 : 0;
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(runs).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
