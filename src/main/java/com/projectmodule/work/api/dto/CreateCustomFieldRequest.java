package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.CustomFieldValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/projects/{id}/custom-fields}. */
public record CreateCustomFieldRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        @NotNull(message = "must not be null")
        CustomFieldValueType valueType,

        @NotBlank(message = "must not be blank")
        String value) {
}
