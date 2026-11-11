package com.projectmodule.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.account.domain.AuthSession;
import com.projectmodule.account.domain.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** Runs against real PostgreSQL; see {@code src/test/resources/application-test.yml}. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class AccountPersistenceTest {

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    private UserAccount savedAccount(String email) {
        return accountRepository.saveAndFlush(UserAccount.register(email, "Ada", "hash"));
    }

    @Test
    @DisplayName("an account is found by its normalized address")
    void findsByEmail() {
        savedAccount("Ada@Example.com");

        assertThat(accountRepository.findByEmail("ada@example.com")).isPresent();
        assertThat(accountRepository.existsByEmail("ada@example.com")).isTrue();
    }

    @Test
    @DisplayName("the database refuses a second account with the same address")
    void emailIsUnique() {
        savedAccount("ada@example.com");

        assertThatThrownBy(() -> savedAccount("ADA@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a session is found by its token hash")
    void findsSessionByHash() {
        UserAccount account = savedAccount("ada@example.com");
        String hash = "a".repeat(64);
        sessionRepository.saveAndFlush(AuthSession.start(account.getId(), hash, Instant.now().plusSeconds(60)));

        assertThat(sessionRepository.findByTokenHash(hash)).isPresent();
    }

    @Test
    @DisplayName("only an account's expired sessions are removed")
    void deletesOnlyExpiredSessions() {
        UserAccount account = savedAccount("ada@example.com");
        Instant now = Instant.now();
        AuthSession expired = sessionRepository.saveAndFlush(
                AuthSession.start(account.getId(), "b".repeat(64), now.minusSeconds(60)));
        AuthSession live = sessionRepository.saveAndFlush(
                AuthSession.start(account.getId(), "c".repeat(64), now.plusSeconds(60)));

        sessionRepository.deleteExpired(account.getId(), now);

        assertThat(sessionRepository.existsById(expired.getId())).isFalse();
        assertThat(sessionRepository.existsById(live.getId())).isTrue();
    }
}
