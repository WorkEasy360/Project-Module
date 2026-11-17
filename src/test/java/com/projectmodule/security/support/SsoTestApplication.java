package com.projectmodule.security.support;

import com.projectmodule.common.api.GlobalExceptionHandler;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.config.RequestContextConfiguration;
import com.projectmodule.dashboard.api.DashboardController;
import com.projectmodule.security.SecurityConfiguration;
import com.projectmodule.security.backchannel.BackchannelLogoutController;
import com.projectmodule.security.backchannel.ReceivedLogoutTokens;
import com.projectmodule.security.identity.FederatedIdentityStore;
import com.projectmodule.security.web.AuthController;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The real security chain, the real sign-in classes, the real back-channel logout endpoint, the real
 * exception handler and the real dashboard controller, with no database: users are mapped, and accepted
 * logout tokens remembered, by in-memory stores, and the dashboard service is a mock. This is the full-stack configuration the sign-in tests drive with MockMvc, so what they prove is
 * what the application's security chain does, not what a test-only chain does.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import({SecurityConfiguration.class, ApiConfiguration.class, RequestContextConfiguration.class,
        GlobalExceptionHandler.class, AuthController.class, DashboardController.class,
        BackchannelLogoutController.class, SsoTestApplication.InMemoryIdentities.class,
        SsoTestApplication.InMemoryReceivedLogoutTokens.class, SsoTestApplication.Probe.class})
public class SsoTestApplication {

    /** Same mapping rule as the PostgreSQL store, held in memory. */
    public static class InMemoryIdentities implements FederatedIdentityStore {

        private final Map<String, UUID> byIdentity = new ConcurrentHashMap<>();

        @Override
        public synchronized ExternalUserId resolve(String issuer, String subject, String email, String displayName) {
            String key = issuer + "|" + subject;
            UUID existing = byIdentity.get(key);
            if (existing != null) {
                return ExternalUserId.of(existing);
            }
            UUID candidate = null;
            try {
                candidate = subject.length() == 36 ? UUID.fromString(subject) : null;
            } catch (IllegalArgumentException notUuid) {
                // a random id below
            }
            if (candidate == null || byIdentity.containsValue(candidate)) {
                candidate = UUID.randomUUID();
            }
            byIdentity.put(key, candidate);
            return ExternalUserId.of(candidate);
        }

        public int size() {
            return byIdentity.size();
        }

        public void clear() {
            byIdentity.clear();
        }
    }

    /** Same contract as the PostgreSQL ledger, held in memory; can be told to fail like a database would. */
    public static class InMemoryReceivedLogoutTokens implements ReceivedLogoutTokens {

        private final Set<String> accepted = ConcurrentHashMap.newKeySet();
        private volatile RuntimeException failNext;

        @Override
        public synchronized boolean acceptOnce(String issuer, String jti, Instant keepUntil, Runnable endSessions) {
            RuntimeException failure = failNext;
            if (failure != null) {
                failNext = null;
                throw failure;
            }
            String key = issuer + "|" + jti;
            if (accepted.contains(key)) {
                return false;
            }
            endSessions.run();
            accepted.add(key);
            return true;
        }

        /** The next call fails as an unreachable database would, recording nothing. */
        public void failNextWith(RuntimeException failure) {
            this.failNext = failure;
        }

        public void clear() {
            accepted.clear();
            failNext = null;
        }
    }

    /** Two endpoints under /api/v1: a read that says who the request is, and a write. */
    @RestController
    public static class Probe {

        private final RequestContext context;

        public Probe(RequestContext context) {
            this.context = context;
        }

        @GetMapping("/api/v1/test/whoami")
        public Map<String, String> whoami() {
            return Map.of(
                    "user", context.userId().map(ExternalUserId::toString).orElse(""),
                    "organization", context.organizationId().map(Object::toString).orElse(""));
        }

        @PostMapping("/api/v1/test/write")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void write() {
            // Nothing: it exists so that a write reaches a controller only when the chain lets it.
        }
    }
}
