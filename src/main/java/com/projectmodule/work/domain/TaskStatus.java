package com.projectmodule.work.domain;

/**
 * A task's status: To Do, In Progress, Blocked and Done. Declared in the order a board shows its
 * columns.
 *
 * <p>People move a task between these freely through {@code PATCH /tasks/{id}}, so a task flagged
 * by mistake, or completed too early, can be put back. Overdue is not a status: a task is overdue
 * when it is past its due date and not done.
 */
public enum TaskStatus {

    /** The initial status of every task. */
    TODO,

    /** Someone is working on it. */
    IN_PROGRESS,

    /** Manually flagged as blocked. */
    BLOCKED,

    /** Done. */
    COMPLETED
}
