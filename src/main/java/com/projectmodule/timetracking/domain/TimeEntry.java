package com.projectmodule.timetracking.domain;

import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Time one person spent on a task on one day. */
@Entity
@Table(name = "time_entries")
public class TimeEntry extends BaseEntity {

    private static final int MINUTES_IN_A_DAY = 1440;
    private static final int NOTE_MAX_LENGTH = 500;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "note", length = NOTE_MAX_LENGTH)
    private String note;

    @Column(name = "billable", nullable = false)
    private boolean billable;

    /** For JPA only. */
    protected TimeEntry() {
    }

    private TimeEntry(UUID id, UUID taskId, UUID projectId, UUID userId, LocalDate workDate, int minutes,
                      String note, boolean billable) {
        super(id);
        if (minutes <= 0 || minutes > MINUTES_IN_A_DAY) {
            throw new ValidationException("Time must be more than 0 and at most 24 hours");
        }
        if (workDate == null) {
            throw new ValidationException("Date is required");
        }
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new ValidationException("Note must be at most " + NOTE_MAX_LENGTH + " characters");
        }
        this.taskId = taskId;
        this.projectId = projectId;
        this.userId = userId;
        this.workDate = workDate;
        this.minutes = minutes;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.billable = billable;
    }

    public static TimeEntry log(UUID taskId, UUID projectId, UUID userId, LocalDate workDate, int minutes,
                                String note, boolean billable) {
        return new TimeEntry(UuidV7.generate(), taskId, projectId, userId, workDate, minutes, note, billable);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public int getMinutes() {
        return minutes;
    }

    public Optional<String> getNote() {
        return Optional.ofNullable(note);
    }

    public boolean isBillable() {
        return billable;
    }
}
