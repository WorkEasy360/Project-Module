package com.projectmodule.work.api.dto;

import java.util.UUID;

/**
 * One item's outcome within a {@link BulkArchiveResponse}, per
 * {@code docs/project/20-BULK-ACTIONS-SPEC.md} §4. {@code error} is {@code null} on success,
 * otherwise the message the individual archive endpoint's {@code ProblemDetail} would already
 * have produced for this id.
 */
public record BulkArchiveResultResponse(UUID id, boolean succeeded, String error) {
}
