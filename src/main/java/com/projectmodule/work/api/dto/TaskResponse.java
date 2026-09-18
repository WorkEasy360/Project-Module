package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representation of a task, returned by create, get, list and update. */
public record TaskResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        LocalDate dueDate,
        UUID assigneeId,
        TaskStatus status,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
