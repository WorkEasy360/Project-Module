package com.projectmodule.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The PostgreSQL-backed identity mapping: the {@code V14} migration, the entity mapping, the user-id rule
 * and the race between two first sign-ins. Runs without the usual per-test rollback because the store opens
 * its own transactions (a unique-constraint violation must not poison a surrounding one).
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, like every other persistence test here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaFederatedIdentityStore.class, FederatedIdentityPersistenceTest.Config.class})
class FederatedIdentityPersistenceTest {

    private static final String ISSUER = "https://app.workeasy360.com";

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private JpaFederatedIdentityStore store;

    @Autowired
    private FederatedIdentityRepository repository;

    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("a sub that is a UUID is the user id, and stays so at every later sign-in")
    void uuidSubjectIsTheUserId() {
        UUID sub = UUID.randomUUID();

        ExternalUserId first = store.resolve(ISSUER, sub.toString(), "pat@example.com", "Pat");
        ExternalUserId second = store.resolve(ISSUER, sub.toString(), "new@example.com", "Pat P.");

        assertThat(first.value()).isEqualTo(sub);
        assertThat(second).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(1);
        FederatedIdentity row = repository.findByIssuerAndSubject(ISSUER, sub.toString()).orElseThrow();
        assertThat(row.getEmail()).as("profile data is refreshed").isEqualTo("new@example.com");
        assertThat(row.getDisplayName()).isEqualTo("Pat P.");
        assertThat(row.getCreatedAt()).isEqualTo(Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    @DisplayName("a sub that is not a UUID gets a generated user id, stable across sign-ins")
    void opaqueSubject() {
        ExternalUserId first = store.resolve(ISSUER, "auth0|abc", null, null);
        ExternalUserId second = store.resolve(ISSUER, "auth0|abc", "x@example.com", null);
        ExternalUserId other = store.resolve(ISSUER, "auth0|def", null, null);

        assertThat(second).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
        assertThat(first.value().toString()).isNotEqualTo("auth0|abc");
    }

    @Test
    @DisplayName("a UUID sub already bound to a different subject does not take over that user id")
    void uuidAlreadyBoundElsewhere() {
        ExternalUserId opaque = store.resolve(ISSUER, "somebody-else", null, null);

        // A subject that is spelled exactly like somebody else's user id.
        ExternalUserId lookalike = store.resolve(ISSUER, opaque.value().toString(), null, null);

        assertThat(lookalike).isNotEqualTo(opaque);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("the same sub under another issuer is another person: it never adopts the first one's user id")
    void issuersAreSeparate() {
        UUID sub = UUID.randomUUID();

        ExternalUserId here = store.resolve(ISSUER, sub.toString(), null, null);
        ExternalUserId there = store.resolve("https://other.example", sub.toString(), null, null);

        assertThat(here.value()).isEqualTo(sub);
        assertThat(there.value()).isNotEqualTo(sub);
        assertThat(repository.count()).isEqualTo(2);
        // ...and each stays itself on later sign-ins.
        assertThat(store.resolve(ISSUER, sub.toString(), null, null)).isEqualTo(here);
        assertThat(store.resolve("https://other.example", sub.toString(), null, null)).isEqualTo(there);
    }

    @Test
    @DisplayName("the database enforces unique (issuer, subject) and unique (issuer, user id)")
    void constraints() {
        UUID user = UUID.randomUUID();
        repository.saveAndFlush(new FederatedIdentity(UUID.randomUUID(), ISSUER, "s1", user, null, null, Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new FederatedIdentity(UUID.randomUUID(), ISSUER, "s1", UUID.randomUUID(), null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(
                new FederatedIdentity(UUID.randomUUID(), ISSUER, "s2", user, null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(
                new FederatedIdentity(UUID.randomUUID(), "https://other.example", "s3", user, null, null,
                        Instant.now())))
                .as("a user id belongs to one identity, whichever issuer vouches for it")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(
                new FederatedIdentity(UUID.randomUUID(), ISSUER, "  ", UUID.randomUUID(), null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("overlong e-mail and name are clipped rather than failing the sign-in")
    void profileIsClipped() {
        store.resolve(ISSUER, "clip", "e".repeat(400), "n".repeat(300));

        FederatedIdentity row = repository.findByIssuerAndSubject(ISSUER, "clip").orElseThrow();
        assertThat(row.getEmail()).hasSize(320);
        assertThat(row.getDisplayName()).hasSize(200);
    }

    @Test
    @DisplayName("two first sign-ins of the same person at once produce one identity and one user id")
    void concurrentFirstSignIns() throws Exception {
        UUID sub = UUID.randomUUID();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ExternalUserId>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<ExternalUserId> task = () -> {
                    start.await();
                    return store.resolve(ISSUER, sub.toString(), "p@example.com", "P");
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            Set<ExternalUserId> users = new HashSet<>();
            for (Future<ExternalUserId> result : results) {
                users.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }

            assertThat(users).containsExactly(ExternalUserId.of(sub));
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isTrue();
        }
    }
}
