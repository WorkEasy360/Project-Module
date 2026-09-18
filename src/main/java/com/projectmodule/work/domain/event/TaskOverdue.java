package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Task.markOverdue()}. Corresponds to {@code task.overdue}. */
public record TaskOverdue(UUID taskId) implements TaskDomainEvent {
}
