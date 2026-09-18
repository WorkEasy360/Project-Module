package com.projectmodule.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Shared mapping for mutable entities: identity, timestamps and optimistic locking.
 *
 * <p>Identifiers are supplied by the application rather than the database, so an entity is
 * complete and comparable from the moment it is constructed rather than only after a flush.
 *
 * <p>Append-only tables deliberately do not extend this class. They have no version and no
 * updated timestamp because nothing ever updates them.
 *
 * <p>Implements {@link Persistable}: {@link #version} is a primitive {@code long}, and Spring
 * Data JPA's default new-entity check does not use a primitive version field to decide
 * {@code isNew()} (a primitive cannot represent "unset"), so it falls back to "is the id null?"
 * — always false here, since ids are application-assigned before the first save. Without this,
 * {@code repository.save(...)} calls {@code entityManager.merge(...)} instead of
 * {@code persist(...)} for a brand-new entity. For a transient instance never before persisted,
 * {@code merge()} returns a <em>different</em> managed object copying only JPA-mapped state —
 * silently dropping any {@code @Transient} field, such as a subclass's pending domain events.
 * {@link #isNew()} restores the correct persist path; {@link #version} keeps doing its own,
 * separate job of optimistic-lock conflict detection, unaffected by this change.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * {@code true} until this instance has actually been persisted or was loaded from the
     * database. {@code @Transient}: purely in-memory bookkeeping for JPA's new-entity check,
     * never a column.
     */
    @Transient
    private boolean isNew = true;

    /** For JPA only. */
    protected BaseEntity() {
    }

    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Flips {@link #isNew} once this instance is actually persisted or loaded from the database. */
    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Equality is by identifier.
     *
     * <p>Safe here because identifiers are assigned at construction: an entity that has not
     * been persisted still has a stable identity, so adding one to a collection before saving
     * does not change its hash afterwards.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity entity)) {
            return false;
        }
        return getClass().equals(other.getClass()) && Objects.equals(id, entity.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
