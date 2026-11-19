package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Something that happened to a {@link com.projectmodule.work.domain.Task}. */
public sealed interface TaskDomainEvent
        permits TaskCreated, TaskUpdated, TaskAssigned, TaskCompleted, TaskBlocked {

    UUID taskId();
}
