package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.TaskResponse;
import com.projectmodule.work.domain.Task;
import org.springframework.stereotype.Component;

/** Maps {@link Task} to its API representation. Plain, hand-written mapping. */
@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getName(),
                task.getDescription().orElse(null),
                task.getDueDate().orElse(null),
                task.getAssigneeId().map(id -> id.value()).orElse(null),
                task.getStatus(),
                task.isArchived(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion());
    }
}
