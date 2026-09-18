package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A named grouping of tasks within a project, optionally under a {@link Phase}.
 *
 * <p>Raises no domain event: {@code docs/project/05-EVENTS.md} defines no Task List events.
 * This is a deliberate absence, not an oversight — see {@code TaskListApplicationService}.
 * The project and phase are referenced by plain id, the same convention {@code ProjectMember}
 * already uses.
 */
@Entity
@Table(name = "task_lists")
public class TaskList extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected TaskList() {
    }

    private TaskList(UUID id, UUID projectId, UUID phaseId, String name) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.phaseId = phaseId;
        this.name = validateName(name);
    }

    /**
     * Creates a task list.
     *
     * @param phaseId the owning phase, or {@code null} if this list belongs directly to the
     *                project with no phase grouping
     * @throws ValidationException if the name is blank or too long
     */
    public static TaskList create(UUID projectId, UUID phaseId, String name) {
        return new TaskList(UuidV7.generate(), projectId, phaseId, name);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void reassignPhase(UUID newPhaseId) {
        requireNotArchived("reassign the phase of");
        this.phaseId = newPhaseId;
    }

    /**
     * Archives the task list. Deleting a task list means archiving it, consistent with
     * {@code Project}.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Task list is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived task list");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Task list name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Task list name must be at most " + NAME_MAX_LENGTH + " characters");
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

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
