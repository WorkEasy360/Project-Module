package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Raised once by {@code Task.recordUpdated()}. Corresponds to {@code task.updated}.
 *
 * <p>Not raised by the individual field mutators, for the same reason {@code ProjectUpdated}
 * and {@code PhaseUpdated} are not: a single {@code PATCH} may call several of them, and this
 * raises exactly one event regardless.
 */
public record TaskUpdated(UUID taskId) implements TaskDomainEvent {
}
