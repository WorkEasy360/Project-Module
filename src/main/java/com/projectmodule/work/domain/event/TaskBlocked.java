package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Task.markBlocked()}. Corresponds to {@code task.blocked}. */
public record TaskBlocked(UUID taskId) implements TaskDomainEvent {
}
