package com.projectmodule.integration.adapter;

import com.projectmodule.integration.port.CalendarPort;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link CalendarPort}: the Calendar module does not exist yet, so
 * nothing is actually synced. Logs the request and never claims a calendar was updated.
 */
@Component
public class NoopCalendarAdapter implements CalendarPort {

    private static final Logger log = LoggerFactory.getLogger(NoopCalendarAdapter.class);

    @Override
    public void syncProjectSchedule(UUID projectId, String projectName, LocalDate startDate, LocalDate targetEndDate) {
        log.debug("CalendarPort.syncProjectSchedule(): no Calendar module integration is "
                + "configured; not synced. projectId={} startDate={} targetEndDate={}",
                projectId, startDate, targetEndDate);
    }
}
