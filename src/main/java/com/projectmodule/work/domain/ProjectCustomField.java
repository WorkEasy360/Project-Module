package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A named, typed, project-scoped custom attribute — one combined field definition and its
 * current value in a single row.
 *
 * <p>Specified in {@code docs/project/11-CUSTOMFIELD-SPEC.md} (approved), since
 * {@code docs/project/01-SPEC.md}/{@code 03-DATABASE.md} document {@code ProjectCustomField}
 * only as a bare entity name. Per the approved specification: custom fields attach only to a
 * Project (not to Task/Risk/Issue/Decision), there is no reusable field-definition registry
 * shared across projects, {@code valueType} is immutable after creation, and there are no
 * domain events — the approved spec defines none for CustomField.
 *
 * <p>Unlike every other entity's plain {@code description}, {@code value}'s validity depends on
 * the declared {@link CustomFieldValueType}: {@code NUMBER} must parse as a number, {@code DATE}
 * as an ISO-8601 date, {@code BOOLEAN} as exactly {@code true}/{@code false}; {@code TEXT} has no
 * further constraint beyond being non-blank.
 */
@Entity
@Table(name = "project_custom_fields")
public class ProjectCustomField extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20, updatable = false)
    private CustomFieldValueType valueType;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected ProjectCustomField() {
    }

    private ProjectCustomField(UUID id, UUID projectId, String name, CustomFieldValueType valueType, String value) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        this.valueType = Objects.requireNonNull(valueType, "valueType is required");
        this.value = validateValue(valueType, value);
    }

    /**
     * Creates a custom field.
     *
     * @throws ValidationException if the name is blank or too long, or the value is blank or
     *                              does not satisfy the declared value type
     */
    public static ProjectCustomField create(UUID projectId, String name, CustomFieldValueType valueType, String value) {
        return new ProjectCustomField(UuidV7.generate(), projectId, name, valueType, value);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    /**
     * Replaces the field's value. {@code valueType} cannot be changed — see the type-level
     * documentation.
     *
     * @throws ValidationException if the new value is blank or does not satisfy this field's
     *                              value type
     */
    public void changeValue(String newValue) {
        requireNotArchived("change the value of");
        this.value = validateValue(this.valueType, newValue);
    }

    /**
     * Archives the custom field. Deleting one means archiving it, consistent with
     * {@code Project}. Raises no event: the approved specification defines none for CustomField.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Custom field is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived custom field");
        }
    }

    private static String validateName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Custom field name must not be blank");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("Custom field name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validateValue(CustomFieldValueType valueType, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Custom field value must not be blank");
        }
        String trimmed = candidate.trim();
        switch (valueType) {
            case TEXT -> {
                // Any non-blank value is valid; already checked above.
            }
            case NUMBER -> {
                try {
                    Double.parseDouble(trimmed);
                } catch (NumberFormatException e) {
                    throw new ValidationException("Custom field value must be a valid number: " + trimmed);
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(trimmed);
                } catch (DateTimeParseException e) {
                    throw new ValidationException("Custom field value must be a valid ISO-8601 date: " + trimmed);
                }
            }
            case BOOLEAN -> {
                if (!trimmed.equals("true") && !trimmed.equals("false")) {
                    throw new ValidationException("Custom field value must be exactly \"true\" or \"false\": " + trimmed);
                }
            }
        }
        return trimmed;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public CustomFieldValueType getValueType() {
        return valueType;
    }

    public String getValue() {
        return value;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
