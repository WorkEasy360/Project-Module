package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body for {@code POST /api/v1/projects/{id}/phases}. */
public record CreatePhaseRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate) {
}
