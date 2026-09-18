package com.projectmodule.integration.port;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound port to the external Calendar module — the "Calendar Service" integration point
 * named in {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>Scoped to exactly the schedule data this module already owns:
 * {@code Project.startDate}/{@code Project.targetEndDate}. This module does not own calendar
 * events, reminders or attendees, and this port does not expose operations for them.
 */
public interface CalendarPort {

    /**
     * Informs the calendar module of a project's current planned window.
     *
     * @param startDate      the project's start date, or {@code null} if not set
     * @param targetEndDate  the project's target end date, or {@code null} if not set
     */
    void syncProjectSchedule(UUID projectId, String projectName, LocalDate startDate, LocalDate targetEndDate);
}
