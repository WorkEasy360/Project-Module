package com.projectmodule.work.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One date-bearing item on a project's calendar, per {@code docs/project/17-CALENDAR-SPEC.md}.
 * {@code entityType} is a plain String ({@code "TASK"}, {@code "MILESTONE"}, {@code "PHASE"}) —
 * the same choice {@code SearchResultResponse} already made, for the same reason: no existing
 * cross-entity type discriminator to extend. {@code date} is set for TASK/MILESTONE and {@code
 * null} for PHASE; {@code startDate}/{@code endDate} are set for PHASE and {@code null} for
 * TASK/MILESTONE.
 */
public record CalendarEntryResponse(
        String entityType,
        UUID id,
        String name,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate) {
}
