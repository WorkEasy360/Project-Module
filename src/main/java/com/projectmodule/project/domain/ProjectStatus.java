package com.projectmodule.project.domain;

/**
 * Lifecycle state of a project: Active, On Hold or Completed.
 *
 * <p>Archived is deliberately absent from this enum. A project is archived by setting its
 * archive timestamp, which is orthogonal to status: an archived project keeps the status it
 * held when it was archived, so the record of how it ended is not overwritten.
 */
public enum ProjectStatus {

    /** Work is under way. Every new project starts here. */
    ACTIVE,

    /** Paused, expected to resume. */
    ON_HOLD,

    /** Finished. */
    COMPLETED;

    /** Whether work may still change on a project in this state. */
    public boolean isOpen() {
        return this == ACTIVE || this == ON_HOLD;
    }
}
