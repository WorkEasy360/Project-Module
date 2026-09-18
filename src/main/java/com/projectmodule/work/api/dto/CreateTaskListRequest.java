package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/task-lists}.
 *
 * <p>{@code phaseId} is optional: a task list may belong directly to the project with no phase
 * grouping.
 */
public record CreateTaskListRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        UUID phaseId) {
}
