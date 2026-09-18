package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/tasks/{id}/dependencies}.
 *
 * <p>The dependent task is the path's {@code {id}}; this only supplies the task it depends on.
 */
public record CreateDependencyRequest(

        @NotNull(message = "must not be null")
        UUID prerequisiteTaskId) {
}
