package com.projectmodule.project.api.dto;

import com.projectmodule.project.domain.ProjectRole;
import java.time.Instant;
import java.util.UUID;

/** Representation of a project membership, returned by every member endpoint. */
public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        ProjectRole role,
        Instant joinedAt,
        long version) {
}
