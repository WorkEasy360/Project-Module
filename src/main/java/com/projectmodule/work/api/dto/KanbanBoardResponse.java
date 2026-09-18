package com.projectmodule.work.api.dto;

import java.util.List;

/**
 * A project's Kanban board, per {@code docs/project/16-KANBAN-SPEC.md}: every
 * {@code TaskStatus} column, always present in declared enum order, even when empty.
 */
public record KanbanBoardResponse(List<KanbanColumnResponse> columns) {
}
