package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of a task list, returned by create, get, list and update. */
public record TaskListResponse(
        UUID id,
        UUID projectId,
        UUID phaseId,
        String name,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
