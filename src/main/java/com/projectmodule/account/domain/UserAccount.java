package com.projectmodule.account.domain;

import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Someone who can sign in. Its id is the user id the rest of the module stores, and its
 * organization id is the tenant the rest of the module is scoped by.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

    /** Failed attempts allowed before the account is locked. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** How long a lock lasts. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_role", nullable = false, length = 20)
    private WorkspaceRole workspaceRole;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** For JPA only. */
    protected UserAccount() {
    }

    private UserAccount(UUID id, UUID organizationId, String email, String displayName, String passwordHash,
                        WorkspaceRole workspaceRole) {
        super(id);
        if (displayName == null || displayName.isBlank()) {
            throw new ValidationException("Name is required");
        }
        this.organizationId = organizationId;
        this.email = normalizeEmail(email);
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        this.workspaceRole = workspaceRole;
    }

    /** A new account in a new workspace of its own, which it therefore administers. */
    public static UserAccount register(String email, String displayName, String passwordHash) {
        return new UserAccount(UuidV7.generate(), UuidV7.generate(), email, displayName, passwordHash,
                WorkspaceRole.ADMIN);
    }

    /** A new account joining an existing workspace through an invitation. */
    public static UserAccount joinWorkspace(UUID organizationId, String email, String displayName,
                                            String passwordHash) {
        return new UserAccount(UuidV7.generate(), organizationId, email, displayName, passwordHash,
                WorkspaceRole.MEMBER);
    }

    /**
     * An account for someone who used the product before email sign-in, keeping the user and
     * organization ids their existing work belongs to.
     */
    public static UserAccount claimExisting(UUID userId, UUID organizationId, String email, String displayName,
                                            String passwordHash, WorkspaceRole role) {
        return new UserAccount(userId, organizationId, email, displayName, passwordHash, role);
    }

    /** The form every address is stored and looked up in. */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void rename(String newDisplayName) {
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new ValidationException("Name is required");
        }
        this.displayName = newDisplayName.trim();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a wrong password, locking the account once the limit is reached. */
    public void recordFailedLogin(Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public WorkspaceRole getWorkspaceRole() {
        return workspaceRole;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
}
