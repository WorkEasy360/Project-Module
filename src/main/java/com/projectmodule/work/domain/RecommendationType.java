package com.projectmodule.work.domain;

/**
 * The kind of AI recommendation, per {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md} §3 —
 * exhaustive for V1. Each maps to one P4 roadmap capability that has an honest, non-mutating
 * (suggest-only) meaning; "AI Project Creation" has none and is not represented here.
 */
public enum RecommendationType {
    /** Suggests how to break a task into subtasks. Requires a {@code resourceId} (the task). */
    TASK_BREAKDOWN,
    /** Suggests improvements to a task. Requires a {@code resourceId} (the task). */
    TASK_IMPROVEMENT,
    /** A project-wide summary ("Project Copilot"). No {@code resourceId}; optional {@code question}. */
    PROJECT_SUMMARY,
    /** Analysis of the project's current risks. No {@code resourceId}, no {@code question}. */
    RISK_ANALYSIS,
    /** A hypothetical-scenario analysis. Requires a {@code question}. */
    WHAT_IF
}
