package com.projectmodule.project.application.authorization;

/**
 * A single capability that can be checked against a project.
 *
 * <p>Permissions rather than roles are checked at the point of use, so that the role-to-
 * permission mapping can change without touching call sites.
 *
 * <p>Covers the P0 foundation only. Permissions for work items, automation and AI approval
 * are added alongside the features that need them.
 */
public enum ProjectPermission {

    VIEW_PROJECT,
    EDIT_PROJECT,
    ARCHIVE_PROJECT,
    MANAGE_MEMBERS,
    MANAGE_SETTINGS
}
