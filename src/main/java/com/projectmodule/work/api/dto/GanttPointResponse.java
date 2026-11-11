package com.projectmodule.work.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A task or milestone rendered as a Gantt point event at its due date, per
 * {@code docs/project/19-GANTT-SPEC.md} §2 — not a fabricated bar. A task that also has a start
 * date carries it, so it can be drawn as a real bar.
 */
public record GanttPointResponse(UUID id, String name, LocalDate date, LocalDate startDate) {

    public GanttPointResponse(UUID id, String name, LocalDate date) {
        this(id, name, date, null);
    }
}
