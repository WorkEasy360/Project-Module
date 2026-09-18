package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.DecisionResponse;
import com.projectmodule.work.domain.Decision;
import org.springframework.stereotype.Component;

/** Maps {@link Decision} to its API representation. Plain, hand-written mapping. */
@Component
public class DecisionMapper {

    public DecisionResponse toResponse(Decision decision) {
        return new DecisionResponse(
                decision.getId(),
                decision.getProjectId(),
                decision.getName(),
                decision.getDescription().orElse(null),
                decision.getDecidedBy().map(id -> id.value()).orElse(null),
                decision.isArchived(),
                decision.getCreatedAt(),
                decision.getUpdatedAt(),
                decision.getVersion());
    }
}
