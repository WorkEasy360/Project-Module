package com.projectmodule.collaboration.domain;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
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
 * One entry in the audit trail of a project.
 *
 * <p>Append-only: there are no mutators, no version column and no updated timestamp, because
 * an audit record that can be edited is not an audit record. The database reinforces this by
 * refusing to delete a project while activity for it remains.
 *
 * <p>Every actor is recorded with its {@link ActorType}, so a change made by a person, by the
 * AI and by an automation run stay distinguishable after the fact rather than merging into one
 * anonymous history.
 *
 * <p>Implements {@link Persistable}: the id is application-assigned (via {@link UuidV7}) and
 * there is no {@code @Version} column, so without this Spring Data JPA's default new-entity
 * check ("is the id null?") always answers no, and {@code repository.save(...)} routes every
 * row through {@code entityManager.merge(...)} instead of {@code persist(...)} — which is why
 * the row was never actually inserted. {@link #isNew()} restores the correct persist path: a
 * freshly constructed instance is new until proven otherwise by actually being persisted or
 * loaded from the database.
 */
@Entity
@Table(name = "project_activity")
public class ProjectActivity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * {@code true} until this instance has actually been persisted or was loaded from the
     * database. {@code @Transient}: purely in-memory bookkeeping for JPA's new-entity check,
     * never a column. There is no update path for this entity — once {@link #isNew} becomes
     * {@code false} it never needs to become {@code true} again.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 60)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "summary", nullable = false, updatable = false, length = 500)
    private String summary;

    /**
     * Structured detail of the change, as JSON.
     *
     * <p>Held as text and mapped to the JSON type of the database by Hibernate, which keeps
     * the entity free of any vendor-specific type.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", updatable = false)
    private String payload;

    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** For JPA only. */
    protected ProjectActivity() {
    }

    private ProjectActivity(UUID projectId,
                            ActivityType activityType,
                            ActorType actorType,
                            ExternalUserId actorId,
                            String summary,
                            String payload,
                            String correlationId) {
        this.id = UuidV7.generate();
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.activityType = Objects.requireNonNull(activityType, "activityType is required");
        this.actorType = Objects.requireNonNull(actorType, "actorType is required");
        this.summary = Objects.requireNonNull(summary, "summary is required");
        this.actorId = actorId == null ? null : actorId.value();
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();

        if (actorType != ActorType.SYSTEM && this.actorId == null) {
            throw new BusinessRuleViolationException(
                    "An actor identifier is required for actor type " + actorType);
        }
    }

    /** Records an action taken by an identified actor. */
    public static ProjectActivity record(UUID projectId,
                                         ActivityType activityType,
                                         ActorType actorType,
                                         ExternalUserId actorId,
                                         String summary,
                                         String payload,
                                         String correlationId) {
        return new ProjectActivity(projectId, activityType, actorType, actorId,
                summary, payload, correlationId);
    }

    /**
     * Records an action using the current caller context, which is the usual path: the actor
     * and correlation identifier are taken from whoever initiated the work.
     */
    public static ProjectActivity record(UUID projectId,
                                         ActivityType activityType,
                                         RequestContext context,
                                         String summary,
                                         String payload) {
        return new ProjectActivity(projectId, activityType, context.actorType(),
                context.userId().orElse(null), summary, payload, context.correlationId());
    }

    /** Records an action taken by the module itself, with no human or AI actor. */
    public static ProjectActivity recordSystemAction(UUID projectId,
                                                     ActivityType activityType,
                                                     String summary,
                                                     String payload) {
        return new ProjectActivity(projectId, activityType, ActorType.SYSTEM, null,
                summary, payload, null);
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

    public UUID getProjectId() {
        return projectId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public Optional<ExternalUserId> getActorId() {
        return Optional.ofNullable(actorId).map(ExternalUserId::of);
    }

    public String getSummary() {
        return summary;
    }

    public Optional<String> getPayload() {
        return Optional.ofNullable(payload);
    }

    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
