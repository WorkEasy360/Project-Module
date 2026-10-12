package com.projectmodule.security.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Which external identity (issuer + subject) is which internal user.
 *
 * <p>See {@code V14__create_federated_identities.sql}. E-mail and display name are profile data as last
 * reported by the issuer; they are refreshed at every sign-in and never used to match or link anything.
 */
@Entity
@Table(name = "federated_identities")
public class FederatedIdentity implements Persistable<UUID> {

    static final int EMAIL_MAX_LENGTH = 320;
    static final int NAME_MAX_LENGTH = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false, updatable = false, length = 512)
    private String issuer;

    @Column(name = "subject", nullable = false, updatable = false, length = 255)
    private String subject;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "display_name", length = NAME_MAX_LENGTH)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_sign_in_at", nullable = false)
    private Instant lastSignInAt;

    @Transient
    private boolean isNew = true;

    /** For JPA only. */
    protected FederatedIdentity() {
    }

    FederatedIdentity(UUID id, String issuer, String subject, UUID userId, String email, String displayName,
                      Instant now) {
        this.id = id;
        this.issuer = issuer;
        this.subject = subject;
        this.userId = userId;
        this.email = clip(email, EMAIL_MAX_LENGTH);
        this.displayName = clip(displayName, NAME_MAX_LENGTH);
        this.createdAt = now;
        this.lastSignInAt = now;
    }

    /** Records another sign-in and the profile the issuer reported this time. */
    void signedIn(String email, String displayName, Instant now) {
        this.email = clip(email, EMAIL_MAX_LENGTH);
        this.displayName = clip(displayName, NAME_MAX_LENGTH);
        this.lastSignInAt = now;
    }

    static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSignInAt() {
        return lastSignInAt;
    }
}
