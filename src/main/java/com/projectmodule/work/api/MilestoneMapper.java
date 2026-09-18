package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.MilestoneResponse;
import com.projectmodule.work.domain.Milestone;
import org.springframework.stereotype.Component;

/** Maps {@link Milestone} to its API representation. Plain, hand-written mapping. */
@Component
public class MilestoneMapper {

    public MilestoneResponse toResponse(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId(),
                milestone.getProjectId(),
                milestone.getPhaseId().orElse(null),
                milestone.getName(),
                milestone.getDescription().orElse(null),
                milestone.getDueDate().orElse(null),
                milestone.getStatus(),
                milestone.isArchived(),
                milestone.getCreatedAt(),
                milestone.getUpdatedAt(),
                milestone.getVersion());
    }
}
