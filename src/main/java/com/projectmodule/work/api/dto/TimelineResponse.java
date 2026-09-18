package com.projectmodule.work.api.dto;

import java.util.List;

/**
 * A project's timeline, per {@code docs/project/18-TIMELINE-SPEC.md}: phases (each with their
 * nested milestones) plus tasks as a separate flat list.
 */
public record TimelineResponse(List<TimelinePhaseResponse> phases, List<TaskResponse> tasks) {
}
