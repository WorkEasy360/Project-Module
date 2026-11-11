package com.projectmodule.account.domain;

/** A person's role in their workspace, separate from their role on any single project. */
public enum WorkspaceRole {

    /** Invites people into the workspace. */
    ADMIN,

    /** Works on the projects they are added to. */
    MEMBER
}
