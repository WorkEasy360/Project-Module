package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceInvitation;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.account.infrastructure.WorkspaceInvitationRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class WorkspaceApplicationServiceTest {

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private WorkspaceInvitationRepository invitationRepository;

    @Mock
    private com.projectmodule.integration.port.EmailPort emailPort;

    private WorkspaceApplicationService service;

    @BeforeEach
    void setUp() {
        AccountApplicationService accounts = new AccountApplicationService(
                accountRepository, sessionRepository, new BCryptPasswordEncoder(4));
        service = new WorkspaceApplicationService(accountRepository, invitationRepository, accounts, emailPort, "http://app");
        lenient().when(invitationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(accountRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static RequestContext contextOf(UserAccount account) {
        return new ImmutableRequestContext(Optional.of(ExternalUserId.of(account.getId())),
                Optional.of(OrganizationId.of(account.getOrganizationId())), "corr", ActorType.HUMAN);
    }

    private UserAccount admin() {
        UserAccount admin = UserAccount.register("admin@example.com", "Admin", "hash");
        when(accountRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        return admin;
    }

    private WorkspaceInvitation stubbedInvitation(UUID organizationId, Instant expiresAt) {
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                organizationId, "ben@example.com", "h", UUID.randomUUID(), expiresAt);
        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        return invitation;
    }

    @Test
    @DisplayName("whoever registers administers their own new workspace")
    void registeringMakesAnAdmin() {
        assertThat(UserAccount.register("a@example.com", "A", "h").getWorkspaceRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("an admin invites an address and receives a one-time link token")
    void adminInvites() {
        UserAccount admin = admin();

        CreatedInvitation created = service.invite(contextOf(admin), " Ben@Example.com ");

        assertThat(created.token()).isNotBlank();
        assertThat(created.invitation().getEmail()).isEqualTo("ben@example.com");
        assertThat(created.invitation().getOrganizationId()).isEqualTo(admin.getOrganizationId());
    }

    @Test
    @DisplayName("a member cannot invite anyone")
    void memberCannotInvite() {
        UserAccount member = UserAccount.joinWorkspace(UUID.randomUUID(), "m@example.com", "M", "h");
        when(accountRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.invite(contextOf(member), "ben@example.com"))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("an address that already has an account cannot be invited")
    void cannotInviteExistingAccount() {
        UserAccount admin = admin();
        when(accountRepository.existsByEmail("ben@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.invite(contextOf(admin), "ben@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("inviting the same address again withdraws the earlier link")
    void reinvitingReplacesTheLink() {
        UserAccount admin = admin();
        WorkspaceInvitation earlier = WorkspaceInvitation.create(admin.getOrganizationId(), "ben@example.com", "old",
                admin.getId(), Instant.now().plusSeconds(60));
        when(invitationRepository.findPending(any(), any())).thenReturn(List.of(earlier));

        service.invite(contextOf(admin), "ben@example.com");

        assertThat(earlier.isUsable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("accepting creates a member in the inviting workspace, with the invited address")
    void acceptJoinsTheWorkspace() {
        UUID organizationId = UUID.randomUUID();
        WorkspaceInvitation invitation = stubbedInvitation(organizationId, Instant.now().plusSeconds(60));

        SignedInAccount signedIn = service.acceptInvitation("token", "Ben", "correct horse", true);

        assertThat(signedIn.account().getOrganizationId()).isEqualTo(organizationId);
        assertThat(signedIn.account().getEmail()).isEqualTo("ben@example.com");
        assertThat(signedIn.account().getWorkspaceRole()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(invitation.isUsable(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("an expired or unknown link is refused")
    void expiredLinkIsRefused() {
        stubbedInvitation(UUID.randomUUID(), Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.previewInvitation("token"))
                .isInstanceOf(BusinessRuleViolationException.class);

        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acceptInvitation("token", "Ben", "correct horse", true))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an admin cannot withdraw another workspace's invitation")
    void cannotRevokeAcrossWorkspaces() {
        UserAccount admin = admin();
        WorkspaceInvitation other = WorkspaceInvitation.create(UUID.randomUUID(), "x@example.com", "h",
                UUID.randomUUID(), Instant.now().plusSeconds(60));
        when(invitationRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.revokeInvitation(contextOf(admin), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(other.isUsable(Instant.now())).isTrue();
    }
}
