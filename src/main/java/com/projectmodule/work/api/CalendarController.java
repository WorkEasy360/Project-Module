package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CalendarResponse;
import com.projectmodule.work.application.CalendarApplicationService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's calendar, per {@code docs/project/17-CALENDAR-SPEC.md}. Holds no
 * business logic or authorization decision; both are delegated to
 * {@link CalendarApplicationService}. No mapper: the application service already returns the
 * API-facing {@code CalendarEntryResponse} directly, the same reasoning
 * {@link SearchController} already established (Calendar has no single domain aggregate of its
 * own to map from either).
 */
@RestController
public class CalendarController {

    private final CalendarApplicationService calendarApplicationService;
    private final RequestContext requestContext;

    public CalendarController(CalendarApplicationService calendarApplicationService, RequestContext requestContext) {
        this.calendarApplicationService = calendarApplicationService;
        this.requestContext = requestContext;
    }

    /** {@code from}/{@code to} are both optional; omitting both returns every dated item. */
    @GetMapping("/projects/{projectId}/calendar")
    public CalendarResponse getCalendar(@PathVariable UUID projectId,
                                        @RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to) {
        return new CalendarResponse(calendarApplicationService.getCalendar(requestContext, projectId, from, to));
    }
}
