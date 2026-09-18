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
 * A choice that was made about a project, recorded for later reference.
 *
 * <p>Specified in {@code docs/project/09-DECISION-SPEC.md} (approved), since
 * {@code docs/project/01-SPEC.md}/{@code 03-DATABASE.md} document Decision only as a bare entity
 * name. Per the approved specification: no status/lifecycle, no domain events, {@code
 * description} is the only free-text field, and {@code decidedBy} is an optional opaque reference
 * to the person who made the decision — which may differ from whoever calls the API to record
 * it — the same convention {@code Task.assigneeId} already uses.
 *
 * <p>The project is referenced by plain id, the same convention {@code ProjectMember} already
 * uses.
 */
@Entity
@Table(name = "decisions")
public class Decision extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Decision() {
    }

    private Decision(UUID id, UUID projectId, String name) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
    }

    /**
     * Records a decision.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Decision create(UUID projectId, String name) {
        return new Decision(UuidV7.generate(), projectId, name);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    /** Records who made the decision, which may differ from the caller recording it. */
    public void attributeTo(ExternalUserId decider) {
        requireNotArchived("change who decided");
        this.decidedBy = Objects.requireNonNull(decider, "decidedBy is required").value();
    }

    /**
     * Archives the decision. Deleting a decision means archiving it, consistent with
     * {@code Project}. Raises no event: the approved specification defines none for Decision.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Decision is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived decision");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Decision name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Decision name must be at most " + NAME_MAX_LENGTH + " characters");
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

    public Optional<ExternalUserId> getDecidedBy() {
        return Optional.ofNullable(decidedBy).map(ExternalUserId::of);
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
