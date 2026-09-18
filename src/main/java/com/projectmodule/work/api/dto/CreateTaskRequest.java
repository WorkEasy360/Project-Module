package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/tasks}.
 *
 * <p>An initial {@code assigneeId} does not raise {@code task.assigned}: creation already
 * raises {@code task.created}, which covers the initial state.
 */
public record CreateTaskRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate dueDate,

        UUID assigneeId) {
}
