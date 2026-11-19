package com.projectmodule.automation.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The canonical catalogue of event type strings a {@link ProjectAutomation} may be configured to
 * trigger on: the project and task events this product raises. Deliberately excludes AI and
 * automation events, so an automation never reacts to another automation's own execution.
 *
 * <p>This is the single source of truth for "is this a real trigger event": {@link ProjectAutomation}
 * validates against it at construction, and the database's {@code ck_..._trigger_event_catalogue}
 * constraint (replaced in {@code V21}) lists the identical values.
 */
public enum AutomationTriggerEvent {
    PROJECT_CREATED("project.created"),
    PROJECT_UPDATED("project.updated"),
    PROJECT_ARCHIVED("project.archived"),
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_ASSIGNED("task.assigned"),
    TASK_COMPLETED("task.completed"),
    TASK_BLOCKED("task.blocked");

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
