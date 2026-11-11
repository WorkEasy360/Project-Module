package com.projectmodule.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 120) String name,
        @NotBlank String password,
        boolean rememberMe) {
}
