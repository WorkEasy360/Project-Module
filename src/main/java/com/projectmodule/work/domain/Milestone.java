package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.work.domain.event.MilestoneAtRisk;
import com.projectmodule.work.domain.event.MilestoneCompleted;
import com.projectmodule.work.domain.event.MilestoneCreated;
import com.projectmodule.work.domain.event.MilestoneDomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A target date a project is working towards, optionally grouped under a {@link Phase}.
 *
 * <p>Plain field edits (name, description, due date) raise no event: only a status transition
 * does, because {@code docs/project/05-EVENTS.md} defines no {@code milestone.updated} event.
 * The project and phase are referenced by plain id, the same convention {@code ProjectMember}
 * already uses.
 */
@Entity
@Table(name = "milestones")
public class Milestone extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    /** Events raised since the last {@link #pullDomainEvents()}. Never persisted. */
    @Transient
    private final List<MilestoneDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MilestoneStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Milestone() {
    }

    private Milestone(UUID id, UUID projectId, UUID phaseId, String name, LocalDate dueDate) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.phaseId = phaseId;
        this.name = validateName(name);
        this.dueDate = dueDate;
        this.status = MilestoneStatus.PENDING;
        registerEvent(new MilestoneCreated(id));
    }

    /**
     * Creates a milestone in its initial, {@link MilestoneStatus#PENDING} state.
     *
     * @param phaseId the owning phase, or {@code null} if this milestone belongs directly to
     *                the project with no phase grouping
     * @throws ValidationException if the name is blank or too long
     */
    public static Milestone create(UUID projectId, UUID phaseId, String name, LocalDate dueDate) {
        return new Milestone(UuidV7.generate(), projectId, phaseId, name, dueDate);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void reschedule(LocalDate newDueDate) {
        requireNotArchived("reschedule");
        this.dueDate = newDueDate;
    }

    public void reassignPhase(UUID newPhaseId) {
        requireNotArchived("reassign the phase of");
        this.phaseId = newPhaseId;
    }

    /**
     * Marks the milestone reached.
     *
     * @throws BusinessRuleViolationException if it is already completed
     */
    public void complete() {
        requireNotArchived("complete");
        if (status == MilestoneStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Milestone is already completed");
        }
        this.status = MilestoneStatus.COMPLETED;
        registerEvent(new MilestoneCompleted(getId()));
    }

    /**
     * Flags the milestone as being in danger of missing its due date.
     *
     * @throws BusinessRuleViolationException if it is already at risk, or already completed
     */
    public void markAtRisk() {
        requireNotArchived("mark at risk");
        if (status == MilestoneStatus.AT_RISK) {
            throw new BusinessRuleViolationException("Milestone is already at risk");
        }
        if (status == MilestoneStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Cannot mark a completed milestone at risk");
        }
        this.status = MilestoneStatus.AT_RISK;
        registerEvent(new MilestoneAtRisk(getId()));
    }

    /**
     * Archives the milestone. Deleting a milestone means archiving it, consistent with
     * {@code Project}. Raises no event: {@code milestone.archived} is not in the current
     * event catalogue.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Milestone is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived milestone");
        }
    }

    private void registerEvent(MilestoneDomainEvent event) {
        domainEvents.add(event);
    }

    /** Returns every event raised since the last call, and clears them. */
    public List<MilestoneDomainEvent> pullDomainEvents() {
        List<MilestoneDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Milestone name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Milestone name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Optional<UUID> getPhaseId() {
        return Optional.ofNullable(phaseId);
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<LocalDate> getDueDate() {
        return Optional.ofNullable(dueDate);
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
