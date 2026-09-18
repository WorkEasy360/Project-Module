package com.projectmodule.automation.domain;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A deterministic, rule-based automation: when {@code triggerEvent} fires for this project and
 * this rule is enabled, its action executes — per the approved
 * {@code docs/project/28-AUTOMATION-SPEC.md}. V1 has no condition sub-language (§3 of that
 * specification): a rule matches on {@code triggerEvent} alone.
 */
@Entity
@Table(name = "project_automations")
public class ProjectAutomation extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "trigger_event", nullable = false, updatable = false, length = 100)
    private String triggerEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 20)
    private AutomationActionType actionType;

    @Column(name = "action_recipient_id")
    private UUID actionRecipientId;

    @Column(name = "action_channel_reference")
    private String actionChannelReference;

    @Column(name = "action_message", nullable = false)
    private String actionMessage;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected ProjectAutomation() {
    }

    private ProjectAutomation(UUID id, UUID projectId, String name, String triggerEvent,
                              AutomationActionType actionType, UUID actionRecipientId,
                              String actionChannelReference, String actionMessage) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.name = validateName(name);
        this.triggerEvent = validateTriggerEvent(triggerEvent);
        this.actionType = Objects.requireNonNull(actionType, "actionType is required");
        this.actionMessage = validateNotBlank(actionMessage, "actionMessage");
        validateAndAssignActionTarget(actionType, actionRecipientId, actionChannelReference);
        this.enabled = true;
    }

    /**
     * Creates an automation rule.
     *
     * @throws ValidationException if {@code name}/{@code actionMessage} is blank,
     *                              {@code triggerEvent} is blank or not a catalogued
     *                              {@link AutomationTriggerEvent}, or the action target required
     *                              by {@code actionType} is missing
     */
    public static ProjectAutomation create(UUID projectId, String name, String triggerEvent,
                                           AutomationActionType actionType, UUID actionRecipientId,
                                           String actionChannelReference, String actionMessage) {
        return new ProjectAutomation(UuidV7.generate(), projectId, name, triggerEvent, actionType,
                actionRecipientId, actionChannelReference, actionMessage);
    }

    public void rename(String newName) {
        requireNotArchived("rename");
        this.name = validateName(newName);
    }

    public void describe(String newDescription) {
        requireNotArchived("update the description of");
        this.description = newDescription;
    }

    public void updateMessage(String newMessage) {
        requireNotArchived("update the message of");
        this.actionMessage = validateNotBlank(newMessage, "actionMessage");
    }

    public void enable() {
        requireNotArchived("enable");
        this.enabled = true;
    }

    public void disable() {
        requireNotArchived("disable");
        this.enabled = false;
    }

    /**
     * Archives the automation. A disabled or archived automation never executes (see
     * {@code docs/project/28-AUTOMATION-SPEC.md} §6).
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("ProjectAutomation is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Whether this rule should fire for {@code eventType} right now — enabled, active, and matching. */
    public boolean matches(String eventType) {
        return enabled && !isArchived() && triggerEvent.equals(eventType);
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived automation");
        }
    }

    private void validateAndAssignActionTarget(AutomationActionType actionType, UUID actionRecipientId,
                                               String actionChannelReference) {
        switch (actionType) {
            case NOTIFY -> {
                if (actionRecipientId == null) {
                    throw new ValidationException("actionRecipientId is required for NOTIFY");
                }
                this.actionRecipientId = actionRecipientId;
            }
            case CHAT_MESSAGE -> {
                if (actionChannelReference == null || actionChannelReference.isBlank()) {
                    throw new ValidationException("actionChannelReference is required for CHAT_MESSAGE");
                }
                this.actionChannelReference = actionChannelReference;
            }
        }
    }

    private static String validateName(String candidate) {
        return validateNotBlank(candidate, "name", NAME_MAX_LENGTH);
    }

    /**
     * Requires {@code candidate} to be one of the catalogued event type strings, per
     * {@code docs/project/28-AUTOMATION-SPEC.md} §2 — not merely non-blank.
     *
     * @throws ValidationException if blank, or not a recognized {@link AutomationTriggerEvent}
     */
    private static String validateTriggerEvent(String candidate) {
        String trimmed = validateNotBlank(candidate, "triggerEvent");
        if (!AutomationTriggerEvent.isValid(trimmed)) {
            throw new ValidationException(
                    "triggerEvent must be one of the catalogued event types; was '" + trimmed + "'");
        }
        return trimmed;
    }

    private static String validateNotBlank(String candidate, String fieldName) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank");
        }
        return candidate.trim();
    }

    private static String validateNotBlank(String candidate, String fieldName, int maxLength) {
        String trimmed = validateNotBlank(candidate, fieldName);
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must be at most " + maxLength + " characters");
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

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public AutomationActionType getActionType() {
        return actionType;
    }

    public Optional<ExternalUserId> getActionRecipientId() {
        return Optional.ofNullable(actionRecipientId).map(ExternalUserId::of);
    }

    public Optional<String> getActionChannelReference() {
        return Optional.ofNullable(actionChannelReference);
    }

    public String getActionMessage() {
        return actionMessage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
