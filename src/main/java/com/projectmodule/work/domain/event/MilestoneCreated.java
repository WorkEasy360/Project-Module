package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Milestone.create(...)}. Corresponds to {@code milestone.created}. */
public record MilestoneCreated(UUID milestoneId) implements MilestoneDomainEvent {
}
