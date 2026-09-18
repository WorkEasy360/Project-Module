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
 * A smaller unit of work within a {@link Task}.
 *
 * <p>Raises no domain event: {@code docs/project/05-EVENTS.md} defines no Subtask events. This
 * is a deliberate absence, not an oversight, the same as {@code TaskList}. Completion is a
 * plain, freely reversible boolean — unlike {@code Task.complete()}, there is no one-way event
 * to protect, so {@link #reopen()} is not restricted.
 *
 * <p>The task is referenced by plain id, the same convention {@code ProjectMember} already
 * uses.
 */
@Entity
@Table(name = "subtasks")
public class Subtask extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Subtask() {
    }

    private Subtask(UUID id, UUID taskId, String name) {
        super(id);
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
        this.name = validateName(name);
        this.completed = false;
    }

    /**
     * Creates a subtask, not completed.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Subtask create(UUID taskId, String name) {
        return new Subtask(UuidV7.generate(), taskId, name);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void complete() {
        requireNotArchived("complete");
        this.completed = true;
    }

    public void reopen() {
        requireNotArchived("reopen");
        this.completed = false;
    }

    /**
     * Archives the subtask. Deleting a subtask means archiving it, consistent with
     * {@code Project}.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Subtask is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived subtask");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Subtask name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Subtask name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public boolean isCompleted() {
        return completed;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
