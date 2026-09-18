package com.projectmodule.work.api.dto;

import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.TaskStatus;
import java.util.Map;

/**
 * A project's summary report, per {@code docs/project/24-REPORTS-SPEC.md}. Every map carries
 * every enum key, including zero counts — the same convention {@code DashboardResponse} already
 * establishes. All counts are over active (non-archived) rows only.
 */
public record ProjectSummaryReportResponse(
        Map<TaskStatus, Long> taskCountByStatus,
        Map<RiskStatus, Long> riskCountByStatus,
        Map<ProjectPriority, Long> riskCountByPriority,
        Map<ProjectPriority, Long> issueCountByPriority,
        long decisionCount,
        long delayedItemCount,
        long brokenDependencyCount) {
}
