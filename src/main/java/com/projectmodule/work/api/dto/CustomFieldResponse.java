package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.CustomFieldValueType;
import java.time.Instant;
import java.util.UUID;

/** Representation of a project custom field, returned by create, list and update. */
public record CustomFieldResponse(
        UUID id,
        UUID projectId,
        String name,
        CustomFieldValueType valueType,
        String value,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
