package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Milestone.markAtRisk()}. Corresponds to {@code milestone.at_risk}. */
public record MilestoneAtRisk(UUID milestoneId) implements MilestoneDomainEvent {
}
