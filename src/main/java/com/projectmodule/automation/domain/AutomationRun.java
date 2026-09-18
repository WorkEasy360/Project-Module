package com.projectmodule.automation.domain;

import com.projectmodule.common.identity.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * The outcome of one {@link ProjectAutomation} execution, per
 * {@code docs/project/28-AUTOMATION-SPEC.md} §5/§6. Append-only, the same convention
 * {@code ProjectActivity} already establishes: there are no mutators, no version column and no
 * updated timestamp, because an execution record that can be edited after the fact is not an
 * audit record.
 *
 * <p>Implements {@link Persistable} directly rather than extending {@code BaseEntity}, for
 * exactly the same reason {@code ProjectActivity} does: the id is application-assigned and
 * there is no {@code @Version} column, so without this Spring Data JPA's default new-entity
 * check would route every save through {@code merge(...)} instead of {@code persist(...)}.
 */
@Entity
@Table(name = "automation_runs")
public class AutomationRun implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "automation_id", nullable = false, updatable = false)
    private UUID automationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "trigger_event", nullable = false, updatable = false, length = 100)
    private String triggerEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private AutomationRunStatus status;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    /** For JPA only. */
    protected AutomationRun() {
    }

    private AutomationRun(UUID automationId, UUID projectId, String triggerEvent,
                          AutomationRunStatus status, String errorMessage) {
        this.id = UuidV7.generate();
        this.automationId = Objects.requireNonNull(automationId, "automationId is required");
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.triggerEvent = Objects.requireNonNull(triggerEvent, "triggerEvent is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.errorMessage = errorMessage;
        this.executedAt = Instant.now();
    }

    public static AutomationRun succeeded(UUID automationId, UUID projectId, String triggerEvent) {
        return new AutomationRun(automationId, projectId, triggerEvent, AutomationRunStatus.SUCCEEDED, null);
    }

    public static AutomationRun failed(UUID automationId, UUID projectId, String triggerEvent, String errorMessage) {
        return new AutomationRun(automationId, projectId, triggerEvent, AutomationRunStatus.FAILED, errorMessage);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** Flips {@link #isNew} once this instance is actually persisted or loaded from the database. */
    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getAutomationId() {
        return automationId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public AutomationRunStatus getStatus() {
        return status;
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
