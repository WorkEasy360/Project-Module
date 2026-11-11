package com.projectmodule.timetracking.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.timetracking.application.TimeTrackingApplicationService;
import com.projectmodule.timetracking.domain.ActiveTimer;
import com.projectmodule.timetracking.domain.TimeEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Time entries on tasks, the caller's timesheet, and their running timer. */
@RestController
public class TimeTrackingController {

    public record LogTimeRequest(@NotNull LocalDate workDate, @NotNull Integer minutes, String note,
                                 boolean billable) {
    }

    public record StopTimerRequest(LocalDate workDate, String note, boolean billable) {
    }

    public record TimeEntryResponse(UUID id, UUID taskId, UUID projectId, UUID userId, LocalDate workDate,
                                    int minutes, String note, boolean billable) {

        static TimeEntryResponse from(TimeEntry entry) {
            return new TimeEntryResponse(entry.getId(), entry.getTaskId(), entry.getProjectId(), entry.getUserId(),
                    entry.getWorkDate(), entry.getMinutes(), entry.getNote().orElse(null), entry.isBillable());
        }
    }

    public record TimerResponse(UUID taskId, Instant startedAt) {

        static TimerResponse from(ActiveTimer timer) {
            return new TimerResponse(timer.getTaskId(), timer.getStartedAt());
        }
    }

    private final TimeTrackingApplicationService service;
    private final RequestContext requestContext;

    public TimeTrackingController(TimeTrackingApplicationService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @PostMapping("/tasks/{taskId}/time-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeEntryResponse logTime(@PathVariable UUID taskId, @Valid @RequestBody LogTimeRequest request) {
        return TimeEntryResponse.from(service.logTime(requestContext, taskId, request.workDate(), request.minutes(),
                request.note(), request.billable()));
    }

    @GetMapping("/tasks/{taskId}/time-entries")
    public List<TimeEntryResponse> listForTask(@PathVariable UUID taskId) {
        return service.listForTask(requestContext, taskId).stream().map(TimeEntryResponse::from).toList();
    }

    @GetMapping("/time-entries/mine")
    public List<TimeEntryResponse> listMine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listMine(requestContext, from, to).stream().map(TimeEntryResponse::from).toList();
    }

    @DeleteMapping("/time-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id) {
        service.deleteEntry(requestContext, id);
    }

    @GetMapping("/timer")
    public ResponseEntity<TimerResponse> currentTimer() {
        return service.currentTimer(requestContext)
                .map(timer -> ResponseEntity.ok(TimerResponse.from(timer)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/tasks/{taskId}/timer")
    @ResponseStatus(HttpStatus.CREATED)
    public TimerResponse startTimer(@PathVariable UUID taskId) {
        return TimerResponse.from(service.startTimer(requestContext, taskId));
    }

    @PostMapping("/timer/stop")
    public TimeEntryResponse stopTimer(@RequestBody(required = false) StopTimerRequest request) {
        String note = request == null ? null : request.note();
        boolean billable = request != null && request.billable();
        LocalDate workDate = request == null ? null : request.workDate();
        return TimeEntryResponse.from(service.stopTimer(requestContext, workDate, note, billable));
    }

    @DeleteMapping("/timer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelTimer() {
        service.cancelTimer(requestContext);
    }
}
