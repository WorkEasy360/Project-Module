package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.SubtaskResponse;
import com.projectmodule.work.domain.Subtask;
import org.springframework.stereotype.Component;

/** Maps {@link Subtask} to its API representation. Plain, hand-written mapping. */
@Component
public class SubtaskMapper {

    public SubtaskResponse toResponse(Subtask subtask) {
        return new SubtaskResponse(
                subtask.getId(),
                subtask.getTaskId(),
                subtask.getName(),
                subtask.getDescription().orElse(null),
                subtask.isCompleted(),
                subtask.isArchived(),
                subtask.getCreatedAt(),
                subtask.getUpdatedAt(),
                subtask.getVersion());
    }
}
