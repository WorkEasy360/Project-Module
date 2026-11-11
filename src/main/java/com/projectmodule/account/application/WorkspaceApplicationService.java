package com.projectmodule.account.application;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceInvitation;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.account.infrastructure.WorkspaceInvitationRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.integration.port.EmailPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is in a workspace, and bringing people into it. Everything is scoped to the caller's own
 * organization, and an invitation from another workspace is reported as not found.
 */
@Service
public class WorkspaceApplicationService {

    static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    private static final String INVALID_INVITATION = "This invitation link is invalid or has expired";

    private final UserAccountRepository accountRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final AccountApplicationService accountApplicationService;
    private final EmailPort emailPort;
    private final String appBaseUrl;

    public WorkspaceApplicationService(UserAccountRepository accountRepository,
                                       WorkspaceInvitationRepository invitationRepository,
                                       AccountApplicationService accountApplicationService,
                                       EmailPort emailPort,
                                       @Value("${projectmodule.app-base-url:http://localhost:8080}") String appBaseUrl) {
        this.emailPort = emailPort;
        this.appBaseUrl = appBaseUrl;
        this.accountRepository = accountRepository;
        this.invitationRepository = invitationRepository;
        this.accountApplicationService = accountApplicationService;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listPeople(RequestContext context) {
        return accountRepository.findByOrganizationIdOrderByDisplayNameAsc(organizationOf(context));
    }

    /**
     * Invites an address. Inviting it again replaces the earlier link, so an admin who lost a link
     * or let one expire can simply send another.
     */
    @Transactional
    public CreatedInvitation invite(RequestContext context, String email) {
        UserAccount admin = requireAdmin(context);
        String normalizedEmail = UserAccount.normalizeEmail(email);
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("This person already has an account");
        }

        Instant now = Instant.now();
        invitationRepository.findPending(admin.getOrganizationId(), now).stream()
                .filter(pending -> pending.getEmail().equals(normalizedEmail))
                .forEach(pending -> pending.revoke(now));

        String token = SecureTokens.generate();
        WorkspaceInvitation invitation = invitationRepository.save(WorkspaceInvitation.create(
                admin.getOrganizationId(), normalizedEmail, SecureTokens.hash(token), admin.getId(),
                now.plus(INVITATION_LIFETIME)));
        emailPort.send(normalizedEmail, admin.getDisplayName() + " invited you to WorkEasyProject",
                admin.getDisplayName() + " invited you to join their workspace on WorkEasyProject.\n\n"
                        + "Set up your account: " + appBaseUrl + "/#/join/" + token
                        + "\n\nThis link works once and expires in " + INVITATION_LIFETIME.toDays() + " days.\n");
        return new CreatedInvitation(invitation, token);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitation> listPendingInvitations(RequestContext context) {
        return invitationRepository.findPending(requireAdmin(context).getOrganizationId(), Instant.now());
    }

    @Transactional
    public void revokeInvitation(RequestContext context, UUID invitationId) {
        UserAccount admin = requireAdmin(context);
        WorkspaceInvitation invitation = invitationRepository.findById(invitationId)
                .filter(found -> found.getOrganizationId().equals(admin.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        invitation.revoke(Instant.now());
    }

    /** What the join page shows. Anyone holding the link may read this. */
    @Transactional(readOnly = true)
    public WorkspaceInvitation previewInvitation(String token) {
        return usableInvitation(token);
    }

    /** Creates the invited person's account, in the inviting workspace, and signs them in. */
    @Transactional
    public SignedInAccount acceptInvitation(String token, String displayName, String password, boolean rememberMe) {
        WorkspaceInvitation invitation = usableInvitation(token);
        String passwordHash = accountApplicationService.hashNewPassword(password);
        UserAccount account = UserAccount.joinWorkspace(
                invitation.getOrganizationId(), invitation.getEmail(), displayName, passwordHash);
        SignedInAccount signedIn = accountApplicationService.saveAndSignIn(account, rememberMe);
        invitation.accept(Instant.now());
        return signedIn;
    }

    private WorkspaceInvitation usableInvitation(String token) {
        return invitationRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(invitation -> invitation.isUsable(Instant.now()))
                .orElseThrow(() -> new BusinessRuleViolationException(INVALID_INVITATION));
    }

    private UserAccount requireAdmin(RequestContext context) {
        UserAccount account = accountRepository.findById(context.requireUserId().value())
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
        if (account.getWorkspaceRole() != WorkspaceRole.ADMIN) {
            throw new AuthorizationException("Only a workspace admin can manage invitations");
        }
        return account;
    }

    private static UUID organizationOf(RequestContext context) {
        return context.organizationId()
                .orElseThrow(() -> new MissingIdentityException("No organization was supplied with this request"))
                .value();
    }
}
