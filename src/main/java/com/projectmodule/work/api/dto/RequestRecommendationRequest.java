package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.RecommendationType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/projects/{projectId}/ai/recommendations}.
 *
 * <p>{@code resourceId}/{@code question} are validated for requiredness by
 * {@code AIRecommendationApplicationService} per {@code type}, not by Bean Validation here — the
 * requirement is conditional on {@code type}, the same reasoning existing conditional business
 * rules use application-service validation rather than annotation-based validation.
 */
public record RequestRecommendationRequest(

        @NotNull(message = "must not be null")
        RecommendationType type,

        UUID resourceId,

        String question) {
}
