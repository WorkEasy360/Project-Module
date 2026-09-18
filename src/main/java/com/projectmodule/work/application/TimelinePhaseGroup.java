package com.projectmodule.work.application;

import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import java.util.List;

/**
 * One phase and its nested milestones, per {@code docs/project/18-TIMELINE-SPEC.md} §2/§3.
 * Internal read-model shape — {@link TaskApplicationService}-style application services return
 * domain objects, not API DTOs; {@code TimelineController} maps this to
 * {@code TimelinePhaseResponse}.
 */
public record TimelinePhaseGroup(Phase phase, List<Milestone> milestones) {
}
