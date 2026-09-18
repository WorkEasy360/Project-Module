package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/projects/{id}/risks}. */
public record CreateRiskRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        @NotNull(message = "must not be null")
        ProjectPriority priority) {
}
