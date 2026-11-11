package com.projectmodule.account.domain;

import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One signed-in browser. Kept in the database rather than encoded in a signed token so that
 * signing out takes effect immediately.
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** For JPA only. */
    protected AuthSession() {
    }

    private AuthSession(UUID id, UUID accountId, String tokenHash, Instant expiresAt) {
        super(id);
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static AuthSession start(UUID accountId, String tokenHash, Instant expiresAt) {
        return new AuthSession(UuidV7.generate(), accountId, tokenHash, expiresAt);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
