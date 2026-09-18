package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.PhaseResponse;
import com.projectmodule.work.domain.Phase;
import org.springframework.stereotype.Component;

/** Maps {@link Phase} to its API representation. Plain, hand-written mapping. */
@Component
public class PhaseMapper {

    public PhaseResponse toResponse(Phase phase) {
        return new PhaseResponse(
                phase.getId(),
                phase.getProjectId(),
                phase.getName(),
                phase.getDescription().orElse(null),
                phase.getStartDate().orElse(null),
                phase.getEndDate().orElse(null),
                phase.isArchived(),
                phase.getCreatedAt(),
                phase.getUpdatedAt(),
                phase.getVersion());
    }
}
