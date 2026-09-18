package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised by {@code Milestone.complete()}. Corresponds to {@code milestone.completed}. */
public record MilestoneCompleted(UUID milestoneId) implements MilestoneDomainEvent {
}
