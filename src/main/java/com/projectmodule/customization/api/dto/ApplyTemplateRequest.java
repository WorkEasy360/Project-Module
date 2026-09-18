package com.projectmodule.customization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/templates/{id}/apply}.
 *
 * <p>{@code name} is the new project's own name, required — distinct from the template's own
 * {@code name}. {@code description}, if supplied, overrides the template's description for the
 * new project; if omitted, the template's own description is used. There is no priority field:
 * the new project's priority always comes from the template's {@code defaultPriority}, per the
 * approved specification.
 */
public record ApplyTemplateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        String description) {
}
