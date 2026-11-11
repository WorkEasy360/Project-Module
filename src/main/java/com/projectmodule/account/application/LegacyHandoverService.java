package com.projectmodule.account.application;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves someone who used the product before email sign-in onto an account without losing their
 * work. Before email sign-in, people were identified by a user id and an organization id kept in
 * their browser. Here they choose an email and password once, and the new account takes those same
 * ids, so every project, task and comment they had is still theirs.
 *
 * <p>This trusts the ids exactly as the product did before, and only once per id: it works only for
 * ids that own existing work and have no account yet. After that, the account's password is the only
 * way in. Turn it off with {@code projectmodule.auth.legacy-handover.enabled=false} once everyone has
 * moved over.
 */
@Service
public class LegacyHandoverService {

    private static final Logger log = LoggerFactory.getLogger(LegacyHandoverService.class);

    private final AccountApplicationService accounts;
    private final UserAccountRepository accountRepository;
    private final boolean enabled;

    @PersistenceContext
    private EntityManager entityManager;

    public LegacyHandoverService(AccountApplicationService accounts,
                                 UserAccountRepository accountRepository,
                                 @Value("${projectmodule.auth.legacy-handover.enabled:true}") boolean enabled) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public SignedInAccount claim(UUID userId, UUID organizationId, String email, String displayName, String password,
                                 boolean rememberMe) {
        if (!enabled) {
            throw new BusinessRuleViolationException("Moving an earlier sign-in is no longer available. Sign in or create an account.");
        }
        if (accountRepository.existsById(userId)) {
            throw new ConflictException("Your workspace already has an account. Sign in with its email and password.");
        }
        if (!ownsExistingWork(userId, organizationId)) {
            throw new BusinessRuleViolationException("We couldn't find earlier work for this sign-in. Create a new account instead.");
        }
        String passwordHash = accounts.hashNewPassword(password);
        // The first person to move over in a workspace administers it; everyone after joins as a member.
        WorkspaceRole role = accountRepository.existsByOrganizationIdAndWorkspaceRole(organizationId, WorkspaceRole.ADMIN)
                ? WorkspaceRole.MEMBER : WorkspaceRole.ADMIN;
        SignedInAccount signedIn = accounts.saveAndSignIn(
                UserAccount.claimExisting(userId, organizationId, email, displayName, passwordHash, role), rememberMe);
        log.info("earlier sign-in moved to an account: user {} in organization {} as {}", userId, organizationId, role);
        return signedIn;
    }

    /** Whether this user owns or belongs to a project in this organization. */
    private boolean ownsExistingWork(UUID userId, UUID organizationId) {
        Object found = entityManager.createNativeQuery("""
                        SELECT EXISTS (
                            SELECT 1 FROM projects p
                            WHERE p.organization_id = :org
                              AND (p.owner_id = :user
                                   OR EXISTS (SELECT 1 FROM project_members m
                                              WHERE m.project_id = p.id AND m.user_id = :user)))
                        """)
                .setParameter("org", organizationId)
                .setParameter("user", userId)
                .getSingleResult();
        return Boolean.TRUE.equals(found);
    }
}
