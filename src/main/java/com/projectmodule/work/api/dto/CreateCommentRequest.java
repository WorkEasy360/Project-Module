package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{id}/comments}.
 *
 * <p>No {@code authorId} field: the author is always the caller
 * ({@code RequestContext.requireUserId()}), never supplied by the request. {@code mentions} names
 * the people to tell; anyone not on the project is ignored.
 */
public record CreateCommentRequest(

        @NotBlank(message = "must not be blank")
        String body,

        List<UUID> mentions) {

    public CreateCommentRequest(String body) {
        this(body, null);
    }
}
