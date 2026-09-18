package com.projectmodule.work.api.dto;

import java.util.List;

/** A project's calendar entries, per {@code docs/project/17-CALENDAR-SPEC.md}. */
public record CalendarResponse(List<CalendarEntryResponse> entries) {
}
