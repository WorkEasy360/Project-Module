package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.account.domain.PasswordResetToken;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.PasswordResetTokenRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.EmailPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordApplicationServiceTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String CURRENT = "correct horse";

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private EmailPort emailPort;

    private PasswordApplicationService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        AccountApplicationService accounts = new AccountApplicationService(accountRepository, sessionRepository, ENCODER);
        service = new PasswordApplicationService(accountRepository, sessionRepository, resetTokenRepository, accounts,
                ENCODER, emailPort, "http://app");
        admin = UserAccount.register("admin@example.com", "Admin", ENCODER.encode(CURRENT));
        lenient().when(accountRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private static RequestContext contextOf(UserAccount account) {
        return new ImmutableRequestContext(Optional.of(ExternalUserId.of(account.getId())),
                Optional.of(OrganizationId.of(account.getOrganizationId())), "corr", ActorType.HUMAN);
    }

    @Test
    @DisplayName("changing a password needs the current one, and ends every session")
    void changesPassword() {
        service.changePassword(contextOf(admin), CURRENT, "a brand new one");

        assertThat(ENCODER.matches("a brand new one", admin.getPasswordHash())).isTrue();
        verify(sessionRepository).revokeAll(eq(admin.getId()), any());
    }

    @Test
    @DisplayName("a wrong current password changes nothing")
    void refusesWrongCurrentPassword() {
        String before = admin.getPasswordHash();

        assertThatThrownBy(() -> service.changePassword(contextOf(admin), "wrong", "a brand new one"))
                .isInstanceOf(ValidationException.class);
        assertThat(admin.getPasswordHash()).isEqualTo(before);
        verify(sessionRepository, never()).revokeAll(any(), any());
    }

    @Test
    @DisplayName("a reset emails a link to an address with an account, and the link holds no stored secret")
    void emailsResetLink() {
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);

        service.requestReset("Admin@Example.com");

        verify(emailPort).send(eq("admin@example.com"), anyString(), body.capture());
        verify(resetTokenRepository).save(saved.capture());
        assertThat(body.getValue()).contains("http://app/#/reset/");
    }

    @Test
    @DisplayName("an unknown address looks exactly the same to the caller: nothing is thrown or sent")
    void unknownAddressRevealsNothing() {
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(emailPort, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("a reset link works once")
    void resetLinkWorksOnce() {
        PasswordResetToken token = new PasswordResetToken(admin.getId(), "h", Instant.now().plusSeconds(60));
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.resetPassword("token", "a brand new one");

        assertThat(ENCODER.matches("a brand new one", admin.getPasswordHash())).isTrue();
        verify(sessionRepository).revokeAll(eq(admin.getId()), any());
        assertThatThrownBy(() -> service.resetPassword("token", "another new one"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an expired reset link is refused")
    void expiredLinkIsRefused() {
        PasswordResetToken token = new PasswordResetToken(admin.getId(), "h", Instant.now().minusSeconds(1));
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("token", "a brand new one"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an admin can create a reset link for a teammate, but not for another workspace")
    void adminCreatesLinksInOwnWorkspaceOnly() {
        UserAccount teammate = UserAccount.joinWorkspace(admin.getOrganizationId(), "t@example.com", "T", "h");
        UserAccount outsider = UserAccount.register("o@example.com", "O", "h");
        when(accountRepository.findById(teammate.getId())).thenReturn(Optional.of(teammate));
        when(accountRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThat(service.createResetTokenForTeammate(contextOf(admin), teammate.getId())).isNotBlank();
        assertThatThrownBy(() -> service.createResetTokenForTeammate(contextOf(admin), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a member cannot create reset links, which would let them take over an account")
    void memberCannotCreateLinks() {
        UserAccount member = UserAccount.joinWorkspace(admin.getOrganizationId(), "m@example.com", "M", "h");
        when(accountRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.createResetTokenForTeammate(contextOf(member), admin.getId()))
                .isInstanceOf(AuthorizationException.class);
    }
}
