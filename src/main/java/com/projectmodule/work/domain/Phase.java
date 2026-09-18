package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.work.domain.event.PhaseCreated;
import com.projectmodule.work.domain.event.PhaseDomainEvent;
import com.projectmodule.work.domain.event.PhaseUpdated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A phase groups a project's work into a stretch of time. Optional: a project may have milestones
 * and task lists with no phase at all, and {@code phaseId} on those is nullable.
 *
 * <p>State changes only through the behaviour methods below, mirroring {@code Project}: no
 * setters, every rule enforced here rather than trusted to a caller. The project is referenced
 * by plain {@code projectId}, not a JPA association, the same convention {@code ProjectMember}
 * already uses — loading a phase never cascades into loading a project.
 */
@Entity
@Table(name = "phases")
public class Phase extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    /** Events raised since the last {@link #pullDomainEvents()}. Never persisted. */
    @Transient
    private final List<PhaseDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Phase() {
    }

    private Phase(UUID id, UUID projectId, String name) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        registerEvent(new PhaseCreated(id));
    }

    /**
     * Creates a phase in its initial state.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Phase create(UUID projectId, String name) {
        return new Phase(UuidV7.generate(), projectId, name);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    /**
     * Sets or clears the planned dates. Either may be null.
     *
     * @throws ValidationException if the end date falls before the start date
     */
    public void schedule(LocalDate start, LocalDate end) {
        requireNotArchived("reschedule");
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("End date " + end + " is before the start date " + start);
        }
        this.startDate = start;
        this.endDate = end;
    }

    /**
     * Marks that this phase was updated, for the audit and event pipeline. Deliberately separate
     * from the field mutators above, for the same reason {@code Project.recordUpdated()} is:
     * one {@code PATCH} may call several of them, and this raises exactly one event regardless.
     *
     * @throws BusinessRuleViolationException if the phase is archived
     */
    public void recordUpdated() {
        requireNotArchived("record an update on");
        registerEvent(new PhaseUpdated(getId()));
    }

    /**
     * Archives the phase. Deleting a phase means archiving it, consistent with {@code Project}.
     * Raises no event: {@code phase.archived} is not in the current event catalogue.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Phase is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived phase");
        }
    }

    private void registerEvent(PhaseDomainEvent event) {
        domainEvents.add(event);
    }

    /** Returns every event raised since the last call, and clears them. */
    public List<PhaseDomainEvent> pullDomainEvents() {
        List<PhaseDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Phase name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Phase name must be at most " + NAME_MAX_LENGTH + " characters");
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

    public Optional<LocalDate> getStartDate() {
        return Optional.ofNullable(startDate);
    }

    public Optional<LocalDate> getEndDate() {
        return Optional.ofNullable(endDate);
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
