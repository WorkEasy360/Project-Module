package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.AIRecommendationResponse;
import com.projectmodule.work.domain.AIRecommendation;
import org.springframework.stereotype.Component;

/** Maps {@link AIRecommendation} to its API representation. Plain, hand-written mapping. */
@Component
public class AIRecommendationMapper {

    public AIRecommendationResponse toResponse(AIRecommendation recommendation) {
        return new AIRecommendationResponse(
                recommendation.getId(),
                recommendation.getProjectId(),
                recommendation.getType(),
                recommendation.getResourceId().orElse(null),
                recommendation.getQuestion().orElse(null),
                recommendation.getTitle(),
                recommendation.getRationale(),
                recommendation.getPayload().orElse(null),
                recommendation.getStatus(),
                recommendation.getRequestedBy().value(),
                recommendation.getRespondedBy().map(id -> id.value()).orElse(null),
                recommendation.getRespondedAt().orElse(null),
                recommendation.getCreatedAt(),
                recommendation.getUpdatedAt(),
                recommendation.getVersion());
    }
}
