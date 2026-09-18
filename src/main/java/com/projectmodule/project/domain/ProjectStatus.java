package com.projectmodule.project.domain;

/**
 * Lifecycle state of a project.
 *
 * <p>Archival is deliberately absent from this enum. A project is archived by setting its
 * archive timestamp, which is orthogonal to status: an archived project keeps the status it
 * held when it was archived, so the record of how it ended is not overwritten.
 */
public enum ProjectStatus {

    /** Being defined; work has not started. */
    PLANNING,

    /** Work is under way. */
    ACTIVE,

    /** Paused, expected to resume. */
    ON_HOLD,

    /** Finished successfully. */
    COMPLETED,

    /** Stopped permanently without completing. */
    CANCELLED;

    /** Whether work may still change on a project in this state. */
    public boolean isOpen() {
        return this == PLANNING || this == ACTIVE || this == ON_HOLD;
    }
}
