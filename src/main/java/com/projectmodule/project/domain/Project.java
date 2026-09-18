package com.projectmodule.project.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.project.domain.event.ProjectArchived;
import com.projectmodule.project.domain.event.ProjectCreated;
import com.projectmodule.project.domain.event.ProjectDomainEvent;
import com.projectmodule.project.domain.event.ProjectUpdated;
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
 * A project: the aggregate root of this module.
 *
 * <p>State is changed only through the behaviour methods below, never through setters. Every
 * one of them enforces the invariants before mutating, which is what makes the AI path safe by
 * construction: an AI tool reaches the same methods a controller does and therefore cannot put
 * a project into a state a person could not.
 *
 * <p>Members are not mapped as a collection here. They are loaded through their own repository
 * so that reading a project never drags in an unbounded membership list.
 *
 * <p>Behaviour that matters for the audit and event pipeline records a
 * {@link ProjectDomainEvent} as it runs; see {@link #pullDomainEvents()}.
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    /**
     * Events raised since the last {@link #pullDomainEvents()}.
     *
     * <p>{@code @Transient}: this is in-memory bookkeeping for the current unit of work, not
     * project state, and is never persisted.
     */
    @Transient
    private final List<ProjectDomainEvent> domainEvents = new ArrayList<>();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ProjectPriority priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_end_date")
    private LocalDate targetEndDate;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Project() {
    }

    private Project(UUID id,
                    OrganizationId organizationId,
                    String name,
                    ExternalUserId ownerId,
                    ProjectStatus status,
                    ProjectPriority priority) {
        super(id);
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId is required").value();
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId is required").value();
        this.name = validateName(name);
        this.status = Objects.requireNonNull(status, "status is required");
        this.priority = Objects.requireNonNull(priority, "priority is required");
        registerEvent(new ProjectCreated(id));
    }

    /**
     * Creates a project in its initial state.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Project create(OrganizationId organizationId,
                                 String name,
                                 ExternalUserId ownerId,
                                 ProjectPriority priority) {
        return new Project(UuidV7.generate(), organizationId, name, ownerId,
                ProjectStatus.PLANNING, priority);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void changeStatus(ProjectStatus newStatus) {
        requireNotArchived("change the status of");
        this.status = Objects.requireNonNull(newStatus, "status is required");
    }

    public void changePriority(ProjectPriority newPriority) {
        requireNotArchived("change the priority of");
        this.priority = Objects.requireNonNull(newPriority, "priority is required");
    }

    /**
     * Marks that this project was updated, for the audit and event pipeline.
     *
     * <p>Deliberately separate from the field mutators above: a single {@code PATCH} request
     * may call several of them (rename, then reschedule, then change priority), and raising an
     * event from each would produce several audit and outbox records for one logical update.
     * The application layer calls this exactly once, after applying every field change a
     * request asked for.
     *
     * @throws BusinessRuleViolationException if the project is archived
     */
    public void recordUpdated() {
        requireNotArchived("record an update on");
        registerEvent(new ProjectUpdated(getId()));
    }

    public void transferOwnership(ExternalUserId newOwner) {
        requireNotArchived("transfer ownership of");
        this.ownerId = Objects.requireNonNull(newOwner, "newOwner is required").value();
    }

    /**
     * Sets or clears the planned dates. Either may be null.
     *
     * @throws ValidationException if the target end date falls before the start date
     */
    public void schedule(LocalDate start, LocalDate targetEnd) {
        requireNotArchived("reschedule");
        if (start != null && targetEnd != null && targetEnd.isBefore(start)) {
            throw new ValidationException(
                    "Target end date " + targetEnd + " is before the start date " + start);
        }
        this.startDate = start;
        this.targetEndDate = targetEnd;
    }

    /**
     * Archives the project. Deleting a project means archiving it; the row is retained so its
     * history, membership and audit trail remain intact.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Project is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
        registerEvent(new ProjectArchived(getId()));
    }

    /**
     * Returns an archived project to active use.
     *
     * <p>Raises no event: {@code project.restored} is not in the current event catalogue
     * (only {@code project.created}, {@code project.updated} and {@code project.archived} are).
     *
     * @throws BusinessRuleViolationException if it is not archived
     */
    public void restore() {
        if (!isArchived()) {
            throw new BusinessRuleViolationException("Project is not archived");
        }
        this.archivedAt = null;
        this.archivedBy = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived project");
        }
    }

    private void registerEvent(ProjectDomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Returns every event raised since the last call, and clears them.
     *
     * <p>Called once by the application layer after a use-case's domain work has fully
     * succeeded, so an event is collected only for a change that actually happened and will be
     * persisted in the same transaction — never for a change that failed validation partway
     * through.
     */
    public List<ProjectDomainEvent> pullDomainEvents() {
        List<ProjectDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Project name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException(
                    "Project name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public OrganizationId getOrganizationId() {
        return OrganizationId.of(organizationId);
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public ProjectPriority getPriority() {
        return priority;
    }

    public Optional<LocalDate> getStartDate() {
        return Optional.ofNullable(startDate);
    }

    public Optional<LocalDate> getTargetEndDate() {
        return Optional.ofNullable(targetEndDate);
    }

    public ExternalUserId getOwnerId() {
        return ExternalUserId.of(ownerId);
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
