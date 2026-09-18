package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Representation of a project comment, returned by create, list and update. */
public record CommentResponse(
        UUID id,
        UUID projectId,
        UUID authorId,
        String body,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
