package com.projectmodule.work.api.dto;

/**
 * A project's health indicators, per {@code docs/project/21-PROJECT-HEALTH-SPEC.md} §3.
 * Deliberately individual counts, not a composite score — no weighting/formula is applied here.
 */
public record ProjectHealthResponse(
        long overdueItemCount,
        long unresolvedRiskCount,
        long unresolvedIssueCount,
        long blockedTaskCount,
        long brokenDependencyCount) {
}
