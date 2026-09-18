package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/comments/{id}}.
 *
 * <p>Unlike other entities' update requests, {@code body} is required rather than
 * optional-when-null: a comment edit has nothing else to leave unchanged. {@code version} is
 * required for the optimistic check. Only the comment's author may perform this update — see
 * {@code ProjectCommentApplicationService}.
 */
public record UpdateCommentRequest(

        @NotBlank(message = "must not be blank")
        String body,

        @NotNull(message = "must not be null")
        Long version) {
}
