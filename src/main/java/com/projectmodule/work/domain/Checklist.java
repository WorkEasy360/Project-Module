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
 * A single checkable line belonging to a {@link com.projectmodule.work.domain.Task}, optionally
 * scoped to one of its {@link Subtask}s.
 *
 * <p>There is no separate "checklist item" entity: {@code docs/project/01-SPEC.md} names only
 * {@code Checklist} among the entities this module owns, so a {@code Checklist} row is itself
 * the checkable unit, not a container. Raises no domain event: {@code docs/project/05-EVENTS.md}
 * defines none for it, the same deliberate absence as {@code TaskList} and {@code Subtask}.
 *
 * <p>The task and subtask are referenced by plain id, the same convention {@code ProjectMember}
 * already uses.
 */
@Entity
@Table(name = "checklists")
public class Checklist extends BaseEntity {

    private static final int TEXT_MAX_LENGTH = 500;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "subtask_id")
    private UUID subtaskId;

    @Column(name = "text", nullable = false, length = TEXT_MAX_LENGTH)
    private String text;

    @Column(name = "checked", nullable = false)
    private boolean checked;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Checklist() {
    }

    private Checklist(UUID id, UUID taskId, UUID subtaskId, String text) {
        super(id);
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
        this.subtaskId = subtaskId;
        this.text = validateText(text);
        this.checked = false;
    }

    /**
     * Creates a checklist line, unchecked.
     *
     * @param subtaskId the owning subtask, or {@code null} if this line belongs directly to the
     *                  task with no subtask scoping
     * @throws ValidationException if the text is blank or too long
     */
    public static Checklist create(UUID taskId, UUID subtaskId, String text) {
        return new Checklist(UuidV7.generate(), taskId, subtaskId, text);
    }

    public void relabel(String newText) {
        requireNotArchived("relabel");
        this.text = validateText(newText);
    }

    public void check() {
        requireNotArchived("check");
        this.checked = true;
    }

    public void uncheck() {
        requireNotArchived("uncheck");
        this.checked = false;
    }

    public void reassignSubtask(UUID newSubtaskId) {
        requireNotArchived("reassign the subtask of");
        this.subtaskId = newSubtaskId;
    }

    /**
     * Archives the checklist line. Deleting one means archiving it, consistent with
     * {@code Project}.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Checklist item is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived checklist item");
        }
    }

    private static String validateText(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Checklist text must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > TEXT_MAX_LENGTH) {
            throw new ValidationException("Checklist text must be at most " + TEXT_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Optional<UUID> getSubtaskId() {
        return Optional.ofNullable(subtaskId);
    }

    public String getText() {
        return text;
    }

    public boolean isChecked() {
        return checked;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
