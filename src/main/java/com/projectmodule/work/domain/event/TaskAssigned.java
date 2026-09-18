package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Task.assign(...)}. Corresponds to {@code task.assigned}. */
public record TaskAssigned(UUID taskId) implements TaskDomainEvent {
}
