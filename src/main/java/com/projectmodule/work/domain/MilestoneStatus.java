package com.projectmodule.work.domain;

/**
 * A milestone's status, driven exactly by the three Milestone events in
 * {@code docs/project/05-EVENTS.md}: {@code milestone.created} produces {@link #PENDING},
 * {@code milestone.completed} produces {@link #COMPLETED}, {@code milestone.at_risk} produces
 * {@link #AT_RISK}. There is no reverse transition, because no event exists for one.
 */
public enum MilestoneStatus {

    /** The initial status of every milestone. */
    PENDING,

    /** Flagged as being in danger of missing its due date. */
    AT_RISK,

    /** Reached. Terminal: a completed milestone cannot become at risk again. */
    COMPLETED
}
