package com.projectmodule.work.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A phase rendered as a Gantt bar, per {@code docs/project/19-GANTT-SPEC.md} §2. */
public record GanttBarResponse(UUID id, String name, LocalDate startDate, LocalDate endDate) {
}
