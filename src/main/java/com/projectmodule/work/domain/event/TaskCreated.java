package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Task.create(...)}. Corresponds to {@code task.created}. */
public record TaskCreated(UUID taskId) implements TaskDomainEvent {
}
