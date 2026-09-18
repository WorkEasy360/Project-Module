package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of a dependency, returned by create, list and update. */
public record DependencyResponse(
        UUID id,
        UUID dependentTaskId,
        UUID prerequisiteTaskId,
        boolean broken,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
