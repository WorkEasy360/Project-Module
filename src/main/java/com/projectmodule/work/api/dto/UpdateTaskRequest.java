package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.TaskRecurrence;
import com.projectmodule.work.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/tasks/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and left unchanged. {@code status}
 * may move to any other status. {@code assigneeId}, when supplied, always raises {@code task.assigned}, even to the same
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
        Long version,

        LocalDate startDate,

        ProjectPriority priority,

        Integer estimateMinutes,

        TaskRecurrence recurrence,

        List<String> tags) {

    public UpdateTaskRequest(String name, String description, LocalDate dueDate, UUID assigneeId,
                             TaskStatus status, Long version) {
        this(name, description, dueDate, assigneeId, status, version, null, null, null, null, null);
    }
}
