package com.projectmodule.account.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteRequest(@NotBlank @Email @Size(max = 254) String email) {
}
