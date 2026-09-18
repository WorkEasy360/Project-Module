package com.projectmodule.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.projectmodule.project.domain.ProjectPriority;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/projects}.
 *
 * <p>{@code organizationId} and {@code ownerId} are deliberately absent here: they come from
 * the caller's {@link com.projectmodule.common.context.RequestContext}, not the request body,
 * so a client cannot create a project in an organization other than its own.
 *
 * <p>The size limit on {@code name} mirrors the constraint the domain enforces in
 * {@link com.projectmodule.project.domain.Project}. Duplicating it here lets a malformed
 * request fail fast with field-level detail instead of a generic domain error.
 */
public record CreateProjectRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        @NotNull(message = "must not be null")
        ProjectPriority priority,

        LocalDate startDate,

        LocalDate targetEndDate) {
}
