package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.account.domain.AuthSession;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    private static final String PASSWORD = "correct horse";
    // Minimum cost: these tests exercise the flow, not the strength of the hash.
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    private AccountApplicationService service;
    private final AtomicReference<AuthSession> savedSession = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(accountRepository, sessionRepository, ENCODER);
        lenient().when(sessionRepository.save(any())).thenAnswer(call -> {
            savedSession.set(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(accountRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private UserAccount existingAccount() {
        UserAccount account = UserAccount.register("ada@example.com", "Ada", ENCODER.encode(PASSWORD));
        when(accountRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    @DisplayName("register stores a hash, never the password, and gives the account its own organization")
    void registerHashesPassword() {
        SignedInAccount result = service.register("  Ada@Example.COM ", " Ada ", PASSWORD, true);

        UserAccount account = result.account();
        assertThat(account.getEmail()).isEqualTo("ada@example.com");
        assertThat(account.getDisplayName()).isEqualTo("Ada");
        assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(ENCODER.matches(PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(account.getOrganizationId()).isNotEqualTo(account.getId());
        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("register refuses an email that is already taken")
    void registerRefusesDuplicate() {
        when(accountRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ADA@example.com", "Ada", PASSWORD, true))
                .isInstanceOf(ConflictException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("two simultaneous registrations report a conflict rather than an internal error")
    void registerRaceIsConflict() {
        when(accountRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.register("ada@example.com", "Ada", PASSWORD, true))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("register refuses a password too short, or too long for BCrypt to read in full")
    void registerChecksPasswordLength() {
        assertThatThrownBy(() -> service.register("a@example.com", "Ada", "short", true))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.register("a@example.com", "Ada", "x".repeat(73), true))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("login succeeds with the right password, whatever the case of the address")
    void loginSucceeds() {
        UserAccount account = existingAccount();

        SignedInAccount result = service.login("ADA@example.com", PASSWORD, false);

        assertThat(result.account()).isSameAs(account);
        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("a wrong password and an unknown address give the same answer")
    void loginDoesNotRevealWhichAddressesExist() {
        existingAccount();
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ada@example.com", "wrong password", true))
                .isInstanceOf(MissingIdentityException.class)
                .hasMessage("Incorrect email or password");
        assertThatThrownBy(() -> service.login("nobody@example.com", "wrong password", true))
                .isInstanceOf(MissingIdentityException.class)
                .hasMessage("Incorrect email or password");
    }

    @Test
    @DisplayName("five wrong passwords lock the account, and even the right one is then refused")
    void repeatedFailuresLockTheAccount() {
        existingAccount();
        for (int attempt = 0; attempt < UserAccount.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> service.login("ada@example.com", "wrong password", true))
                    .hasMessage("Incorrect email or password");
        }

        assertThatThrownBy(() -> service.login("ada@example.com", PASSWORD, true))
                .isInstanceOf(MissingIdentityException.class)
                .hasMessageContaining("Too many failed attempts");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a successful login clears the failure count")
    void successResetsFailures() {
        UserAccount account = existingAccount();
        assertThatThrownBy(() -> service.login("ada@example.com", "wrong password", true));
        assertThat(account.getFailedLoginAttempts()).isEqualTo(1);

        service.login("ada@example.com", PASSWORD, true);

        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("remember me decides how long the session lasts")
    void rememberMeSetsLifetime() {
        existingAccount();

        Instant remembered = service.login("ada@example.com", PASSWORD, true).expiresAt();
        Instant shortLived = service.login("ada@example.com", PASSWORD, false).expiresAt();

        assertThat(remembered).isAfter(Instant.now().plus(AccountApplicationService.REMEMBERED_SESSION).minusSeconds(60));
        assertThat(shortLived).isBefore(Instant.now().plus(AccountApplicationService.SHORT_SESSION).plusSeconds(60));
    }

    @Test
    @DisplayName("a token resolves to its owner, and stops working once signed out")
    void tokenWorksUntilLogout() {
        UserAccount account = existingAccount();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        String token = service.login("ada@example.com", PASSWORD, true).token();
        when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(savedSession.get()));

        assertThat(service.authenticate(token)).isPresent()
                .get().satisfies(identity -> {
                    assertThat(identity.userId().value()).isEqualTo(account.getId());
                    assertThat(identity.organizationId().value()).isEqualTo(account.getOrganizationId());
                });

        service.logout(token);

        assertThat(service.authenticate(token)).isEmpty();
    }

    @Test
    @DisplayName("an expired session is refused, and an unknown token is too")
    void expiredAndUnknownTokensAreRefused() {
        when(sessionRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(AuthSession.start(java.util.UUID.randomUUID(), "h", Instant.now().minusSeconds(1))))
                .thenReturn(Optional.empty());

        assertThat(service.authenticate("expired")).isEmpty();
        assertThat(service.authenticate("unknown")).isEmpty();
    }

    @Test
    @DisplayName("the token itself is never what gets stored")
    void storesOnlyTheTokenHash() {
        existingAccount();
        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);

        String token = service.login("ada@example.com", PASSWORD, true).token();
        service.logout(token);

        verify(sessionRepository).findByTokenHash(hash.capture());
        assertThat(hash.getValue()).isNotEqualTo(token).hasSize(64);
    }
}
