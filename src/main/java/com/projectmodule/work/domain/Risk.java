package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.event.RiskCreated;
import com.projectmodule.work.domain.event.RiskDomainEvent;
import com.projectmodule.work.domain.event.RiskResolved;
import com.projectmodule.work.domain.event.RiskUpdated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A potential problem that could affect a project.
 *
 * <p><b>Not detailed in the documentation beyond its existence</b> ({@code docs/project/01-SPEC.md},
 * {@code docs/project/03-DATABASE.md}) and its three events ({@code docs/project/05-EVENTS.md}):
 * no fields, statuses or priorities are specified anywhere. This shape — {@code name},
 * {@code description}, {@code priority} (reusing the existing {@link ProjectPriority}), and a
 * two-value {@code status} driven by {@code risk.created}/{@code risk.resolved} — is the minimal
 * one consistent with every other work-management entity's conventions, not a documented
 * requirement.
 *
 * <p>Plain field edits (name, description, priority) raise {@code risk.updated}; only the
 * {@code OPEN -> RESOLVED} transition raises its own event, the same discipline {@code Task}
 * status transitions use. There is no {@code risk.reopened} event, so there is no reverse
 * transition. This is P1 CRUD only: no AI risk analysis, prediction or health scoring, which is
 * P3/P4 Intelligence per {@code docs/project/08-ROADMAP.md}.
 *
 * <p>The project is referenced by plain id, the same convention {@code ProjectMember} already
 * uses.
 */
@Entity
@Table(name = "risks")
public class Risk extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    /** Events raised since the last {@link #pullDomainEvents()}. Never persisted. */
    @Transient
    private final List<RiskDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ProjectPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RiskStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Risk() {
    }

    private Risk(UUID id, UUID projectId, String name, ProjectPriority priority) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        this.priority = Objects.requireNonNull(priority, "priority is required");
        this.status = RiskStatus.OPEN;
        registerEvent(new RiskCreated(id));
    }

    /**
     * Creates a risk in its initial, {@link RiskStatus#OPEN} state.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Risk create(UUID projectId, String name, ProjectPriority priority) {
        return new Risk(UuidV7.generate(), projectId, name, priority);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void reprioritize(ProjectPriority newPriority) {
        requireNotArchived("change the priority of");
        this.priority = Objects.requireNonNull(newPriority, "priority is required");
    }

    /**
     * Marks that this risk was updated, for the audit and event pipeline. Deliberately separate
     * from the field mutators above, for the same reason {@code Task.recordUpdated()} is.
     *
     * @throws BusinessRuleViolationException if the risk is archived
     */
    public void recordUpdated() {
        requireNotArchived("record an update on");
        registerEvent(new RiskUpdated(getId()));
    }

    /**
     * Marks the risk resolved. There is no event for leaving {@link RiskStatus#RESOLVED} once
     * set.
     *
     * @throws BusinessRuleViolationException if it is already resolved
     */
    public void resolve() {
        requireNotArchived("resolve");
        if (status == RiskStatus.RESOLVED) {
            throw new BusinessRuleViolationException("Risk is already resolved");
        }
        this.status = RiskStatus.RESOLVED;
        registerEvent(new RiskResolved(getId()));
    }

    /**
     * Archives the risk. Deleting a risk means archiving it, consistent with {@code Project}.
     * Raises no event: {@code risk.archived} is not in the current event catalogue.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Risk is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived risk");
        }
    }

    private void registerEvent(RiskDomainEvent event) {
        domainEvents.add(event);
    }

    /** Returns every event raised since the last call, and clears them. */
    public List<RiskDomainEvent> pullDomainEvents() {
        List<RiskDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Risk name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Risk name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public ProjectPriority getPriority() {
        return priority;
    }

    public RiskStatus getStatus() {
        return status;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
