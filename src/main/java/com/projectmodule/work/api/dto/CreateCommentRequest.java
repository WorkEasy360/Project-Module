package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/projects/{id}/comments}.
 *
 * <p>No {@code authorId} field: the author is always the caller
 * ({@code RequestContext.requireUserId()}), never supplied by the request.
 */
public record CreateCommentRequest(

        @NotBlank(message = "must not be blank")
        String body) {
}
