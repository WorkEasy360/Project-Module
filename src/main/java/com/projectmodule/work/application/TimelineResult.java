package com.projectmodule.work.application;

import com.projectmodule.work.domain.Task;
import java.util.List;

/**
 * A project's timeline, per {@code docs/project/18-TIMELINE-SPEC.md}: phases (each with their
 * nested milestones) plus tasks as a separate flat list — Task has no {@code phaseId} to nest
 * under (§1). Internal read-model shape; {@code TimelineController} maps this to
 * {@code TimelineResponse}.
 */
public record TimelineResult(List<TimelinePhaseGroup> phases, List<Task> tasks) {
}
