package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Task.complete()}. Corresponds to {@code task.completed}. */
public record TaskCompleted(UUID taskId) implements TaskDomainEvent {
}
