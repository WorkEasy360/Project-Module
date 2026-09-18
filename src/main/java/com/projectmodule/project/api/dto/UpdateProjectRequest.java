package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for {@code PATCH /api/v1/projects/{id}}.
 *
 * <p>A field left {@code null} is treated as "not supplied" and is left unchanged; there is no
 * way in this slice to explicitly clear an optional field such as {@code description} back to
 * empty. Every field that is present is applied through the corresponding domain behaviour
 * method, never through a setter, so domain invariants are enforced exactly as they are for a
 * direct domain caller.
 *
 * <p>{@code version} is required: it is the value the client last read, and the update is
 * rejected with a conflict if the project has changed since. Owner transfer is not exposed
 * here; it was not requested for this slice.
 */
public record UpdateProjectRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        ProjectStatus status,

        ProjectPriority priority,

        LocalDate startDate,

        LocalDate targetEndDate,

        @NotNull(message = "must not be null")
        Long version) {
}
