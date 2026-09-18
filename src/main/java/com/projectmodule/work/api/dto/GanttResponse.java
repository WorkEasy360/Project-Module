package com.projectmodule.work.api.dto;

import java.util.List;

/**
 * A project's Gantt view, per {@code docs/project/19-GANTT-SPEC.md}: phases as bars, tasks and
 * milestones as point events, plus the task dependency edges between them.
 */
public record GanttResponse(
        List<GanttBarResponse> phases,
        List<GanttPointResponse> tasks,
        List<GanttPointResponse> milestones,
        List<DependencyResponse> dependencies) {
}
