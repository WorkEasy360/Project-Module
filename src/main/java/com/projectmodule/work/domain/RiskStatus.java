package com.projectmodule.work.domain;

/**
 * A risk's status, driven exactly by the events in {@code docs/project/05-EVENTS.md} that carry
 * a status change: {@code risk.resolved}. There is no {@code risk.reopened} event, so there is
 * no reverse transition out of {@link #RESOLVED} in this slice.
 */
public enum RiskStatus {

    /** The initial status of every risk. */
    OPEN,

    /** Reached. There is no event for leaving this state once set. */
    RESOLVED
}
