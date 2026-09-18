package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/tasks/{id}/checklists}.
 *
 * <p>{@code subtaskId} is optional: a checklist line may belong directly to the task with no
 * subtask scoping.
 */
public record CreateChecklistRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 500, message = "must be at most 500 characters")
        String text,

        UUID subtaskId) {
}
