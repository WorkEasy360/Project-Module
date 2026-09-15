package com.projectmodule.project.application.authorization;

/**
 * A member's role on a single project.
 *
 * <p>Project-level roles are owned by this module. They are distinct from any company-wide
 * role, which belongs to User Management, and are scoped to one project: the same person may
 * hold different roles on different projects.
 */
public enum ProjectRole {

    /** Full control, including deletion and ownership transfer. */
    OWNER,

    /** Plans and runs the project, manages membership, cannot delete it. */
    MANAGER,

    /** Contributes work: creates and updates tasks. */
    MEMBER,

    /** Read-only access. */
    VIEWER
}
