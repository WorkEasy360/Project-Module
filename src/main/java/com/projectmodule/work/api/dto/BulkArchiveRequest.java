package com.projectmodule.work.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request body for a bulk-archive endpoint, per {@code docs/project/20-BULK-ACTIONS-SPEC.md} §4.
 * The 100-id cap is an ordinary input-validation safeguard against an unbounded request body,
 * not a business rule.
 */
public record BulkArchiveRequest(

        @NotEmpty(message = "must not be empty")
        @Size(max = 100, message = "must contain at most 100 ids")
        List<UUID> ids) {
}
