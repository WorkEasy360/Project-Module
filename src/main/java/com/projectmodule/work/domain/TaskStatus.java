package com.projectmodule.work.domain;

/**
 * A task's status. Declared in the order a board shows its columns.
 *
 * <p>People move a task between these freely through {@code PATCH /tasks/{id}}, so a task flagged
 * by mistake, or completed too early, can be put back. {@code OVERDUE} and {@code BLOCKED} are set
 * by a person; "past its due date" is a separate derived view (see delay detection).
 */
public enum TaskStatus {

    /** The initial status of every task. */
    TODO,

    /** Someone is working on it. */
    IN_PROGRESS,

    /** Manually flagged as blocked. */
    BLOCKED,

    /** Manually flagged as overdue. */
    OVERDUE,

    /** Finished. */
    COMPLETED
}
