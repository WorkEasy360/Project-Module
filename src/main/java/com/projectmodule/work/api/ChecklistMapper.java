package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.ChecklistResponse;
import com.projectmodule.work.domain.Checklist;
import org.springframework.stereotype.Component;

/** Maps {@link Checklist} to its API representation. Plain, hand-written mapping. */
@Component
public class ChecklistMapper {

    public ChecklistResponse toResponse(Checklist checklist) {
        return new ChecklistResponse(
                checklist.getId(),
                checklist.getTaskId(),
                checklist.getSubtaskId().orElse(null),
                checklist.getText(),
                checklist.isChecked(),
                checklist.isArchived(),
                checklist.getCreatedAt(),
                checklist.getUpdatedAt(),
                checklist.getVersion());
    }
}
