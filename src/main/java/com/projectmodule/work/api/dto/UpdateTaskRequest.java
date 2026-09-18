package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/tasks/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code status}
 * may only move to {@link TaskStatus#COMPLETED}, {@link TaskStatus#BLOCKED} or
 * {@link TaskStatus#OVERDUE} — the transitions {@code docs/project/05-EVENTS.md} has an event
 * for. Setting it back to {@link TaskStatus#TODO} is rejected; there is no event for that.
 * {@code assigneeId}, when supplied, always raises {@code task.assigned}, even to the same
 * person: reassignment is repeatable, not a one-way transition. {@code version} is required for
 * the optimistic check.
 */
public record UpdateTaskRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate dueDate,

        UUID assigneeId,

        TaskStatus status,

        @NotNull(message = "must not be null")
        Long version) {
}
