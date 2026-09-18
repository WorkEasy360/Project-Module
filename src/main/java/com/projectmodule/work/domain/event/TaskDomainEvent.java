package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.work.domain.Task}.
 *
 * <p>Sealed to exactly the six Task events in {@code docs/project/05-EVENTS.md}.
 */
public sealed interface TaskDomainEvent
        permits TaskCreated, TaskUpdated, TaskAssigned, TaskCompleted, TaskBlocked, TaskOverdue {

    UUID taskId();
}
