package com.projectmodule.events.domain;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * An event awaiting delivery outside this module.
 *
 * <p>The row is written in the same transaction as the state change it describes. If that
 * transaction rolls back the event disappears with it, so an event is never published for a
 * change that did not happen, and a committed change never loses its event.
 *
 * <p>There is no message bus yet. A publisher polls unpublished rows, which keeps delivery
 * correct today and makes adopting a broker later a change of publisher rather than a change
 * to any of the code that raises events.
 *
 * <p>Implements {@link Persistable}: the id is application-assigned (via {@link UuidV7}) and
 * there is no {@code @Version} column, so without this Spring Data JPA's default new-entity
 * check ("is the id null?") always answers no, and {@code repository.save(...)} routes every
 * row through {@code entityManager.merge(...)} instead of {@code persist(...)} — which is why
 * the row was never actually inserted. {@link #isNew()} restores the correct persist path for a
 * freshly constructed instance, while a row loaded by the future publisher (to call
 * {@link #markPublished()} or {@link #markFailed(String)}) is correctly recognised as existing
 * via {@link #markNotNew()}, so that later save still performs a normal update.
 */
@Entity
@Table(name = "outbox")
public class OutboxMessage implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * {@code true} until this instance has actually been persisted or was loaded from the
     * database. {@code @Transient}: purely in-memory bookkeeping for JPA's new-entity check,
     * never a column.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** The event name from the module event catalogue, such as {@code project.created}. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", updatable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    /** Lets a consumer tell which shape of payload it is reading. */
    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    /** For JPA only. */
    protected OutboxMessage() {
    }

    private OutboxMessage(String aggregateType,
                          UUID aggregateId,
                          String eventType,
                          String payload,
                          ActorType actorType,
                          ExternalUserId actorId,
                          String correlationId,
                          int schemaVersion) {
        this.id = UuidV7.generate();
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.payload = Objects.requireNonNull(payload, "payload is required");
        this.actorType = actorType;
        this.actorId = actorId == null ? null : actorId.value();
        this.correlationId = correlationId;
        this.schemaVersion = schemaVersion;
        this.occurredAt = Instant.now();
        this.attemptCount = 0;
    }

    public static OutboxMessage pending(String aggregateType,
                                        UUID aggregateId,
                                        String eventType,
                                        String payload,
                                        ActorType actorType,
                                        ExternalUserId actorId,
                                        String correlationId) {
        return new OutboxMessage(aggregateType, aggregateId, eventType, payload,
                actorType, actorId, correlationId, 1);
    }

    /** Marks the event delivered. */
    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    /**
     * Records a failed delivery attempt so a stuck event is visible rather than silently
     * retried forever.
     */
    public void markFailed(String error) {
        this.attemptCount++;
        this.lastError = error;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** Flips {@link #isNew} once this instance is actually persisted or loaded from the database. */
    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    public Optional<ActorType> getActorType() {
        return Optional.ofNullable(actorType);
    }

    public Optional<ExternalUserId> getActorId() {
        return Optional.ofNullable(actorId).map(ExternalUserId::of);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Optional<Instant> getPublishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }
}
