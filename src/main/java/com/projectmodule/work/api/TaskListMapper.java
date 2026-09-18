package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.TaskListResponse;
import com.projectmodule.work.domain.TaskList;
import org.springframework.stereotype.Component;

/** Maps {@link TaskList} to its API representation. Plain, hand-written mapping. */
@Component
public class TaskListMapper {

    public TaskListResponse toResponse(TaskList taskList) {
        return new TaskListResponse(
                taskList.getId(),
                taskList.getProjectId(),
                taskList.getPhaseId().orElse(null),
                taskList.getName(),
                taskList.getDescription().orElse(null),
                taskList.isArchived(),
                taskList.getCreatedAt(),
                taskList.getUpdatedAt(),
                taskList.getVersion());
    }
}
