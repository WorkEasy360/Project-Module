package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of a subtask, returned by create, list and update. */
public record SubtaskResponse(
        UUID id,
        UUID taskId,
        String name,
        String description,
        boolean completed,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
