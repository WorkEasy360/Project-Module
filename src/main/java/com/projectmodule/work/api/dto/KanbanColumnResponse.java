package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.TaskStatus;
import java.util.List;

/** One column of a {@link KanbanBoardResponse}, per {@code docs/project/16-KANBAN-SPEC.md}. */
public record KanbanColumnResponse(TaskStatus status, List<TaskResponse> tasks) {
}
