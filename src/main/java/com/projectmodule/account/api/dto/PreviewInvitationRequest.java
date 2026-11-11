package com.projectmodule.account.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Sent in a body, not a URL, so the token stays out of access logs. */
public record PreviewInvitationRequest(@NotBlank String token) {
}
