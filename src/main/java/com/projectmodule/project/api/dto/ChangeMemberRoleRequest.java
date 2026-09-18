package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/projects/{id}/members/{memberId}}.
 *
 * <p>Neither the new role nor the member's current role may be {@link ProjectRole#OWNER}: see
 * {@link AddMemberRequest} for why ownership is excluded from generic role management.
 */
public record ChangeMemberRoleRequest(

        @NotNull(message = "must not be null")
        ProjectRole role) {
}
