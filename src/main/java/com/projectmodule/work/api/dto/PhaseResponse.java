package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representation of a phase, returned by create, get, list and update. */
public record PhaseResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
