package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/members}.
 *
 * <p>{@code role} must not be {@link ProjectRole#OWNER}: ownership is established once, at
 * project creation, and reassigning it is a separate, not-yet-implemented operation distinct
 * from ordinary membership management. See {@code ProjectMemberApplicationService}.
 */
public record AddMemberRequest(

        @NotNull(message = "must not be null")
        UUID userId,

        @NotNull(message = "must not be null")
        ProjectRole role) {
}
