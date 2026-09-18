package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.RecommendationStatus;
import com.projectmodule.work.domain.RecommendationType;
import java.time.Instant;
import java.util.UUID;

/** Representation of an AI recommendation, returned by request, list, accept and reject. */
public record AIRecommendationResponse(
        UUID id,
        UUID projectId,
        RecommendationType type,
        UUID resourceId,
        String question,
        String title,
        String rationale,
        String payload,
        RecommendationStatus status,
        UUID requestedBy,
        UUID respondedBy,
        Instant respondedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
