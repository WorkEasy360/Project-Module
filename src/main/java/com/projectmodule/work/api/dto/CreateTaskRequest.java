package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.TaskRecurrence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/tasks}.
 *
 * <p>Only {@code name} is required. An initial {@code assigneeId} does not raise
 * {@code task.assigned}: creation already raises {@code task.created}, which covers the initial
 * state.
 */
public record CreateTaskRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate dueDate,

        UUID assigneeId,

        LocalDate startDate,

        ProjectPriority priority,

        Integer estimateMinutes,

        TaskRecurrence recurrence,

        List<String> tags) {

    public CreateTaskRequest(String name, String description, LocalDate dueDate, UUID assigneeId) {
        this(name, description, dueDate, assigneeId, null, null, null, null, null);
    }
}
