package com.projectmodule.account.application;

import com.projectmodule.account.domain.AuthSession;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating accounts, signing in and out, and resolving a session token to its owner.
 *
 * <p>Passwords are stored only as BCrypt hashes and session tokens only as SHA-256 hashes, so
 * neither can be recovered from the database.
 */
@Service
public class AccountApplicationService implements BearerTokenAuthenticator {

    /** "Remember me" sessions last a month; others last a working day. */
    static final Duration REMEMBERED_SESSION = Duration.ofDays(30);
    static final Duration SHORT_SESSION = Duration.ofHours(12);

    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only reads the first 72 bytes, so a longer password would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final String INVALID_CREDENTIALS = "Incorrect email or password";
    private static final String EMAIL_TAKEN = "An account with this email already exists";

    private final UserAccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when an address is unknown, so both cases take the same time. */
    private final String unmatchableHash;

    public AccountApplicationService(UserAccountRepository accountRepository,
                                     AuthSessionRepository sessionRepository,
                                     PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.unmatchableHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public SignedInAccount register(String email, String displayName, String password, boolean rememberMe) {
        String passwordHash = hashNewPassword(password);
        return saveAndSignIn(UserAccount.register(email, displayName, passwordHash), rememberMe);
    }

    /**
     * Failed attempts must be saved even though the request fails, so this does not roll back for
     * the exception it raises. The message is the same whether the address or the password was
     * wrong, so it does not reveal which addresses have accounts.
     */
    @Transactional(noRollbackFor = MissingIdentityException.class)
    public SignedInAccount login(String email, String password, boolean rememberMe) {
        Instant now = Instant.now();
        Optional<UserAccount> found = accountRepository.findByEmail(UserAccount.normalizeEmail(email));
        if (found.isEmpty()) {
            passwordMatches(password, unmatchableHash);
            throw new MissingIdentityException(INVALID_CREDENTIALS);
        }

        UserAccount account = found.get();
        if (account.isLocked(now)) {
            throw new MissingIdentityException(
                    "Too many failed attempts. Try again in " + UserAccount.LOCK_DURATION.toMinutes() + " minutes.");
        }
        if (!passwordMatches(password, account.getPasswordHash())) {
            account.recordFailedLogin(now);
            throw new MissingIdentityException(INVALID_CREDENTIALS);
        }

        account.recordSuccessfulLogin(now);
        sessionRepository.deleteExpired(account.getId(), now);
        return startSession(account, rememberMe);
    }

    /** For a sign-in another party has already verified, such as Google. */
    SignedInAccount signInWithoutPassword(UserAccount account, boolean rememberMe) {
        account.recordSuccessfulLogin(Instant.now());
        return startSession(account, rememberMe);
    }

    @Transactional
    public UserAccount rename(UUID accountId, String displayName) {
        UserAccount account = getAccount(accountId);
        account.rename(displayName);
        return account;
    }

    /** Ends the session. Unknown tokens are ignored: the caller is signed out either way. */
    @Transactional
    public void logout(String token) {
        sessionRepository.findByTokenHash(SecureTokens.hash(token))
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    /** @throws MissingIdentityException if the account no longer exists */
    @Transactional(readOnly = true)
    public UserAccount getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedIdentity> authenticate(String token) {
        Instant now = Instant.now();
        return sessionRepository.findByTokenHash(SecureTokens.hash(token))
                .filter(session -> session.isActive(now))
                .flatMap(session -> accountRepository.findById(session.getAccountId()))
                .map(account -> new AuthenticatedIdentity(
                        ExternalUserId.of(account.getId()), OrganizationId.of(account.getOrganizationId())));
    }

    /** Checks the password is acceptable, and returns the hash to store. */
    String hashNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (byteLength(password) > MAX_PASSWORD_BYTES) {
            throw new ValidationException("Password is too long");
        }
        return passwordEncoder.encode(password);
    }

    /** Saves a new account, refusing a taken address, and signs it in. */
    SignedInAccount saveAndSignIn(UserAccount newAccount, boolean rememberMe) {
        if (accountRepository.existsByEmail(newAccount.getEmail())) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        newAccount.recordSuccessfulLogin(Instant.now());
        UserAccount account;
        try {
            account = accountRepository.saveAndFlush(newAccount);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous registrations both passed the check above.
            throw new ConflictException(EMAIL_TAKEN);
        }
        return startSession(account, rememberMe);
    }

    private SignedInAccount startSession(UserAccount account, boolean rememberMe) {
        String token = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(rememberMe ? REMEMBERED_SESSION : SHORT_SESSION);
        sessionRepository.save(AuthSession.start(account.getId(), SecureTokens.hash(token), expiresAt));
        return new SignedInAccount(account, token, expiresAt);
    }

    private boolean passwordMatches(String password, String hash) {
        // An over-long password can never have been accepted at registration.
        return byteLength(password) <= MAX_PASSWORD_BYTES && passwordEncoder.matches(password, hash);
    }

    private static int byteLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
