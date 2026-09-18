package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import com.projectmodule.project.domain.ProjectPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A problem that has already occurred and affects a project.
 *
 * <p><b>Not detailed in the documentation beyond its existence</b> ({@code docs/project/01-SPEC.md},
 * {@code docs/project/03-DATABASE.md}): no fields are specified, and unlike {@link Risk} — which
 * has three documented events — {@code docs/project/05-EVENTS.md} has no {@code ## Issue}
 * section at all, so zero Issue events are documented.
 *
 * <p>Taken literally, "implement only documented events" means this entity raises none: there is
 * no {@link com.projectmodule.work.application.WorkEventRecorder} integration for Issue, and no
 * status field, since the only precedent for a status transition ({@code Risk.resolve()}) exists
 * specifically to raise the event that documents it. An issue's only lifecycle transition is the
 * soft-archive every entity already has, which itself raises no event. Fields
 * (name/description/priority, reusing the existing {@link ProjectPriority}) mirror the minimal
 * shape used for {@link Risk}, for consistency.
 *
 * <p>The project is referenced by plain id, the same convention {@code ProjectMember} already
 * uses.
 */
@Entity
@Table(name = "issues")
public class Issue extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ProjectPriority priority;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected Issue() {
    }

    private Issue(UUID id, UUID projectId, String name, ProjectPriority priority) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        this.priority = Objects.requireNonNull(priority, "priority is required");
    }

    /**
     * Creates an issue.
     *
     * @throws ValidationException if the name is blank or too long
     */
    public static Issue create(UUID projectId, String name, ProjectPriority priority) {
        return new Issue(UuidV7.generate(), projectId, name, priority);
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
     * Archives the issue. Deleting an issue means archiving it, consistent with {@code Project}.
     * Raises no event: {@code issue.archived} is not in the current event catalogue — the same
     * as every other entity's archive, and Issue has no documented event at all.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Issue is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived issue");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Issue name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Issue name must be at most " + NAME_MAX_LENGTH + " characters");
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

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
