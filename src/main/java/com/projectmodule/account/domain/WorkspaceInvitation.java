package com.projectmodule.account.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A one-time link that lets the person with this email join a workspace. */
@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** For JPA only. */
    protected WorkspaceInvitation() {
    }

    private WorkspaceInvitation(UUID id, UUID organizationId, String email, String tokenHash, UUID invitedBy,
                                Instant expiresAt) {
        super(id);
        this.organizationId = organizationId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public static WorkspaceInvitation create(UUID organizationId, String email, String tokenHash, UUID invitedBy,
                                             Instant expiresAt) {
        return new WorkspaceInvitation(UuidV7.generate(), organizationId, UserAccount.normalizeEmail(email),
                tokenHash, invitedBy, expiresAt);
    }

    /** Waiting to be accepted: not used, not withdrawn, not expired. */
    public boolean isUsable(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void accept(Instant now) {
        if (!isUsable(now)) {
            throw new BusinessRuleViolationException("This invitation is no longer valid");
        }
        acceptedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null && acceptedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
