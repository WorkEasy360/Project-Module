package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.MilestoneStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representation of a milestone, returned by create, get, list and update. */
public record MilestoneResponse(
        UUID id,
        UUID projectId,
        UUID phaseId,
        String name,
        String description,
        LocalDate dueDate,
        MilestoneStatus status,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
