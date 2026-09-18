package com.projectmodule.work.domain;

/**
 * Lifecycle of an {@link AIRecommendation}, per
 * {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md} §2. {@code EXPIRED} is a named state with
 * no code path in V1 — this module has no scheduler and no TTL is specified anywhere, so nothing
 * ever transitions a recommendation to it.
 */
public enum RecommendationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
