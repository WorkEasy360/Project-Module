package com.projectmodule.account.application;

import com.projectmodule.account.domain.PasswordResetToken;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.PasswordResetTokenRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.integration.port.EmailPort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing, and recovering, a password. Whichever way it changes, every session of that account
 * ends, so anyone else signed in as that person is signed out.
 */
@Service
public class PasswordApplicationService {

    static final Duration RESET_LIFETIME = Duration.ofHours(2);

    private final UserAccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AccountApplicationService accountApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final EmailPort emailPort;
    private final String appBaseUrl;

    public PasswordApplicationService(UserAccountRepository accountRepository,
                                      AuthSessionRepository sessionRepository,
                                      PasswordResetTokenRepository resetTokenRepository,
                                      AccountApplicationService accountApplicationService,
                                      PasswordEncoder passwordEncoder,
                                      EmailPort emailPort,
                                      @Value("${projectmodule.app-base-url:http://localhost:8080}") String appBaseUrl) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.accountApplicationService = accountApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.emailPort = emailPort;
        this.appBaseUrl = appBaseUrl;
    }

    /** All of the caller's sessions end, including this one: they sign in again with the new password. */
    @Transactional
    public void changePassword(RequestContext context, String currentPassword, String newPassword) {
        UserAccount account = callerAccount(context);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ValidationException("Your current password is incorrect");
        }
        setPassword(account, newPassword);
    }

    /**
     * Emails a reset link if the address has an account. The caller sees the same result either
     * way, so this does not reveal which addresses have accounts.
     */
    @Transactional
    public void requestReset(String email) {
        accountRepository.findByEmail(UserAccount.normalizeEmail(email)).ifPresent(account -> {
            String token = createResetToken(account);
            emailPort.send(account.getEmail(), "Reset your WorkEasyProject password",
                    "Use this link to set a new password. It works once and expires in "
                            + RESET_LIFETIME.toHours() + " hours.\n\n" + appBaseUrl + "/#/reset/" + token
                            + "\n\nIf you did not ask for this, you can ignore this email.\n");
        });
    }

    /** Lets an admin help a teammate when no mail server is configured, by sharing the link. */
    @Transactional
    public String createResetTokenForTeammate(RequestContext context, UUID accountId) {
        UserAccount admin = callerAccount(context);
        if (admin.getWorkspaceRole() != WorkspaceRole.ADMIN) {
            throw new AuthorizationException("Only a workspace admin can do this");
        }
        UserAccount teammate = accountRepository.findById(accountId)
                .filter(found -> found.getOrganizationId().equals(admin.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        return createResetToken(teammate);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(found -> found.isUsable(now))
                .orElseThrow(() -> new BusinessRuleViolationException("This link is invalid or has expired"));
        UserAccount account = accountRepository.findById(resetToken.getAccountId())
                .orElseThrow(() -> new BusinessRuleViolationException("This link is invalid or has expired"));
        setPassword(account, newPassword);
        resetToken.markUsed(now);
    }

    private String createResetToken(UserAccount account) {
        String token = SecureTokens.generate();
        resetTokenRepository.save(new PasswordResetToken(
                account.getId(), SecureTokens.hash(token), Instant.now().plus(RESET_LIFETIME)));
        return token;
    }

    private void setPassword(UserAccount account, String newPassword) {
        account.changePassword(accountApplicationService.hashNewPassword(newPassword));
        sessionRepository.revokeAll(account.getId(), Instant.now());
    }

    private UserAccount callerAccount(RequestContext context) {
        return accountRepository.findById(context.requireUserId().value())
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
    }
}
