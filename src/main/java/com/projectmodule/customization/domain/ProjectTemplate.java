package com.projectmodule.customization.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
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
 * A reusable, organization-scoped blueprint for creating a {@link com.projectmodule.project.domain.Project}.
 *
 * <p>Specified in {@code docs/project/12-TEMPLATE-SPEC.md} (approved), since
 * {@code docs/project/01-SPEC.md}/{@code 03-DATABASE.md} document {@code ProjectTemplate} only
 * as a bare entity name. Per the approved specification: V1 is a metadata-only project-default
 * preset — {@code name}, {@code description}, {@code defaultPriority} — not a structural
 * snapshot of Phases/Milestones/Tasks (explicitly out of scope). There are no domain events: the
 * approved spec defines none for Template.
 *
 * <p>Unlike every project-scoped entity in {@code work}/{@code customization.ProjectCustomField},
 * this is scoped by {@code organizationId}, the same convention {@code Project} itself uses
 * (reusable across many projects, so it cannot be scoped to one).
 */
@Entity
@Table(name = "project_templates")
public class ProjectTemplate extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", length = 20)
    private ProjectPriority defaultPriority;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected ProjectTemplate() {
    }

    private ProjectTemplate(UUID id, OrganizationId organizationId, String name, ProjectPriority defaultPriority) {
        super(id);
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId is required").value();
        this.name = validateName(name);
        this.defaultPriority = defaultPriority;
    }

    /**
     * Creates a template.
     *
     * @param defaultPriority may be {@code null} — {@code defaultPriority} is optional (approved
     *                        Decision #4)
     * @throws ValidationException if the name is blank or too long
     */
    public static ProjectTemplate create(OrganizationId organizationId, String name, ProjectPriority defaultPriority) {
        return new ProjectTemplate(UuidV7.generate(), organizationId, name, defaultPriority);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void changeDefaultPriority(ProjectPriority newDefaultPriority) {
        requireNotArchived("change the default priority of");
        this.defaultPriority = Objects.requireNonNull(newDefaultPriority, "defaultPriority is required");
    }

    /**
     * Archives the template. Deleting one means archiving it, consistent with {@code Project}.
     * An archived template cannot be applied (enforced by the application service's active-only
     * lookup, not here). Raises no event: the approved specification defines none for Template.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Template is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived template");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Template name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Template name must be at most " + NAME_MAX_LENGTH + " characters");
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

    public Optional<ProjectPriority> getDefaultPriority() {
        return Optional.ofNullable(defaultPriority);
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
