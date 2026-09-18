package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.RiskResponse;
import com.projectmodule.work.domain.Risk;
import org.springframework.stereotype.Component;

/** Maps {@link Risk} to its API representation. Plain, hand-written mapping. */
@Component
public class RiskMapper {

    public RiskResponse toResponse(Risk risk) {
        return new RiskResponse(
                risk.getId(),
                risk.getProjectId(),
                risk.getName(),
                risk.getDescription().orElse(null),
                risk.getPriority(),
                risk.getStatus(),
                risk.isArchived(),
                risk.getCreatedAt(),
                risk.getUpdatedAt(),
                risk.getVersion());
    }
}
