package com.projectmodule.work.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One delayed item, per {@code docs/project/22-DELAY-DETECTION-SPEC.md} §3. {@code entityType}
 * is a plain String ({@code "TASK"}, {@code "MILESTONE"}, {@code "PHASE"}) — the same choice
 * {@code SearchResultResponse}/{@code CalendarEntryResponse} already use. {@code dueDate} is the
 * task/milestone's {@code dueDate} or the phase's {@code endDate}.
 */
public record DelayedItemResponse(String entityType, UUID id, String name, LocalDate dueDate, long daysOverdue) {
}
