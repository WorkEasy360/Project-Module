package com.projectmodule.work.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One matched record from {@code GET /api/v1/projects/{projectId}/search}, per
 * {@code docs/project/13-SEARCH-SPEC.md} §4.
 *
 * <p>{@code resourceType} is a plain {@code String} — {@code "TASK"}, {@code "RISK"},
 * {@code "ISSUE"} or {@code "DECISION"} (approved Decision #4) — not a shared enum, since there
 * is no existing cross-entity type discriminator anywhere in this codebase to extend.
 * {@code archived} is always {@code false} in V1 (archived records are excluded — approved §7),
 * kept in the shape only so a future "include archived" toggle would not need a breaking
 * response-shape change.
 */
public record SearchResultResponse(
        String resourceType,
        UUID id,
        UUID projectId,
        String name,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {
}
