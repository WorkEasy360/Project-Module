package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.work.domain.Milestone}.
 *
 * <p>Sealed to exactly the three Milestone events in {@code docs/project/05-EVENTS.md}. There is
 * deliberately no "updated" event: a plain field edit (name, description, due date) raises
 * nothing, only a status transition does.
 */
public sealed interface MilestoneDomainEvent permits MilestoneCreated, MilestoneCompleted, MilestoneAtRisk {

    UUID milestoneId();
}
