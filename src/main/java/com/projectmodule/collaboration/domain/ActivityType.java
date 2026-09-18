package com.projectmodule.collaboration.domain;

/**
 * What happened, as recorded in the audit trail.
 *
 * <p>Stored as text rather than constrained by the database, so adding a new kind of activity
 * does not require a migration. Covers the P0 foundation; further values are added alongside
 * the features that raise them.
 */
public enum ActivityType {

    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_STATUS_CHANGED,
    PROJECT_ARCHIVED,
    PROJECT_RESTORED,
    MEMBER_ADDED,
    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED,
    PHASE_CREATED,
    PHASE_UPDATED,
    MILESTONE_CREATED,
    MILESTONE_COMPLETED,
    MILESTONE_AT_RISK,
    TASK_CREATED,
    TASK_UPDATED,
    TASK_ASSIGNED,
    TASK_COMPLETED,
    TASK_BLOCKED,
    TASK_OVERDUE,
    DEPENDENCY_CREATED,
    DEPENDENCY_BROKEN,
    RISK_CREATED,
    RISK_UPDATED,
    RISK_RESOLVED,
    AI_RECOMMENDATION_CREATED,
    AI_RECOMMENDATION_ACCEPTED,
    AI_RECOMMENDATION_REJECTED
}
