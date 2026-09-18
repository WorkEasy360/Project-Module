package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.work.domain.event.DependencyBroken;
import com.projectmodule.work.domain.event.DependencyCreated;
import com.projectmodule.work.domain.event.DependencyDomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A directed "depends on" relationship between two {@link com.projectmodule.work.domain.Task}s:
 * {@code dependentTaskId} depends on {@code prerequisiteTaskId}.
 *
 * <p>Self-dependency is rejected here, at construction, since it needs no data beyond the two
 * ids supplied. Cross-project and duplicate/direct-reverse-cycle protection need to look up
 * both tasks and existing dependencies, so those live in
 * {@code DependencyApplicationService}, which has repository access this entity does not.
 * Deeper transitive cycle detection across the whole dependency graph is not implemented here:
 * {@code docs/project/08-ROADMAP.md} lists "Dependency analysis" under P3 Intelligence,
 * separate from this P1 entity.
 *
 * <p>{@code broken} is a one-way, guarded transition, the same discipline
 * {@code Milestone}/{@code Task} status transitions use: there is no
 * {@code dependency.unbroken}/recovered event, so there is no reverse transition. It is set
 * explicitly by a person through {@code PATCH}, never computed automatically — there is no
 * dependency-driven automation in this slice.
 *
 * <p>The two tasks are referenced by plain id, the same convention {@code ProjectMember}
 * already uses.
 */
@Entity
@Table(name = "dependencies")
public class Dependency extends BaseEntity {

    /** Events raised since the last {@link #pullDomainEvents()}. Never persisted. */
    @Transient
    private final List<DependencyDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "dependent_task_id", nullable = false, updatable = false)
    private UUID dependentTaskId;

    @Column(name = "prerequisite_task_id", nullable = false, updatable = false)
    private UUID prerequisiteTaskId;

    @Column(name = "broken", nullable = false)
    private boolean broken;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Dependency() {
    }

    private Dependency(UUID id, UUID dependentTaskId, UUID prerequisiteTaskId) {
        super(id);
        this.dependentTaskId = Objects.requireNonNull(dependentTaskId, "dependentTaskId is required");
        this.prerequisiteTaskId = Objects.requireNonNull(prerequisiteTaskId, "prerequisiteTaskId is required");
        if (dependentTaskId.equals(prerequisiteTaskId)) {
            throw new ValidationException("A task cannot depend on itself");
        }
        this.broken = false;
        registerEvent(new DependencyCreated(id));
    }

    /**
     * Creates a dependency: {@code dependentTaskId} depends on {@code prerequisiteTaskId}.
     *
     * @throws ValidationException if the two task ids are the same
     */
    public static Dependency create(UUID dependentTaskId, UUID prerequisiteTaskId) {
        return new Dependency(UuidV7.generate(), dependentTaskId, prerequisiteTaskId);
    }

    /**
     * Flags the dependency as broken. There is no reverse-transition event in this slice.
     *
     * @throws BusinessRuleViolationException if it is already broken
     */
    public void markBroken() {
        requireNotArchived("mark broken");
        if (broken) {
            throw new BusinessRuleViolationException("Dependency is already broken");
        }
        this.broken = true;
        registerEvent(new DependencyBroken(getId()));
    }

    /**
     * Archives the dependency. Deleting one means archiving it, consistent with
     * {@code Project}. Raises no event: {@code dependency.archived} is not in the current event
     * catalogue.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Dependency is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived dependency");
        }
    }

    private void registerEvent(DependencyDomainEvent event) {
        domainEvents.add(event);
    }

    /** Returns every event raised since the last call, and clears them. */
    public List<DependencyDomainEvent> pullDomainEvents() {
        List<DependencyDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public UUID getDependentTaskId() {
        return dependentTaskId;
    }

    public UUID getPrerequisiteTaskId() {
        return prerequisiteTaskId;
    }

    public boolean isBroken() {
        return broken;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
