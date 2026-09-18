package com.projectmodule.customization.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/templates}.
 *
 * <p>No {@code organizationId} field: it comes from the caller's
 * {@link com.projectmodule.common.context.RequestContext}, not the request body, the same
 * convention {@code CreateProjectRequest} already uses.
 */
public record CreateTemplateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description,

        ProjectPriority defaultPriority) {
}
