package com.projectmodule.work.domain;

/**
 * A task's status, driven exactly by the events in {@code docs/project/05-EVENTS.md} that carry
 * a status change: {@code task.completed}, {@code task.blocked}, {@code task.overdue}. There is
 * no {@code IN_PROGRESS} value: no event exists for reaching one, so none is modelled.
 *
 * <p>{@code OVERDUE} and {@code BLOCKED} are set explicitly, by a person, through
 * {@code PATCH /tasks/{id}} — there is no scheduler or automated delay detection in this slice
 * (that is P3 Intelligence). This keeps manual workflows fully functional without it.
 */
public enum TaskStatus {

    /** The initial status of every task. */
    TODO,

    /** Manually flagged as blocked. */
    BLOCKED,

    /** Manually flagged as overdue. */
    OVERDUE,

    /** Reached. There is no event for leaving this state once set. */
    COMPLETED
}
