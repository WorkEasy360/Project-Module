package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of a checklist line, returned by create, list and update. */
public record ChecklistResponse(
        UUID id,
        UUID taskId,
        UUID subtaskId,
        String text,
        boolean checked,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
