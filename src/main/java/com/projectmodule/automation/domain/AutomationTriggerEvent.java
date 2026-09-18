package com.projectmodule.automation.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The canonical catalogue of event type strings a {@link ProjectAutomation} may be configured to
 * trigger on — exactly {@code docs/project/05-EVENTS.md}'s `## Project`/`## Phase`/
 * `## Milestone`/`## Task`/`## Dependency`/`## Risk` sections, per
 * {@code docs/project/28-AUTOMATION-SPEC.md} §2. Deliberately excludes the doc's own
 * `## AI`/`## Automation` sections — an automation reacting to another automation's own
 * execution is not specified and would need its own cycle-prevention design (same document,
 * same section).
 *
 * <p>This is the single source of truth for "is this a real trigger event": {@link ProjectAutomation}
 * validates against it at construction, and the database's {@code ck_..._trigger_event_catalogue}
 * constraint (added in {@code V13}) lists the identical values — no third list exists anywhere
 * else in this codebase.
 */
public enum AutomationTriggerEvent {
    PROJECT_CREATED("project.created"),
    PROJECT_UPDATED("project.updated"),
    PROJECT_ARCHIVED("project.archived"),
    PHASE_CREATED("phase.created"),
    PHASE_UPDATED("phase.updated"),
    MILESTONE_CREATED("milestone.created"),
    MILESTONE_COMPLETED("milestone.completed"),
    MILESTONE_AT_RISK("milestone.at_risk"),
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_ASSIGNED("task.assigned"),
    TASK_COMPLETED("task.completed"),
    TASK_OVERDUE("task.overdue"),
    TASK_BLOCKED("task.blocked"),
    DEPENDENCY_CREATED("dependency.created"),
    DEPENDENCY_BROKEN("dependency.broken"),
    RISK_CREATED("risk.created"),
    RISK_UPDATED("risk.updated"),
    RISK_RESOLVED("risk.resolved");

    private final String eventType;

    AutomationTriggerEvent(String eventType) {
        this.eventType = eventType;
    }

    /** The literal event type string, exactly as {@code WorkEventRecorder}/{@code ProjectEventRecorder} raise it. */
    public String eventType() {
        return eventType;
    }

    /** Whether {@code candidate} is one of the catalogued event type strings. */
    public static boolean isValid(String candidate) {
        return fromEventType(candidate).isPresent();
    }

    public static Optional<AutomationTriggerEvent> fromEventType(String candidate) {
        return Arrays.stream(values()).filter(v -> v.eventType.equals(candidate)).findFirst();
    }
}
