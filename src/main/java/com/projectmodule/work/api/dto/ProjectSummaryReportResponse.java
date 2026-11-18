package com.projectmodule.work.api.dto;

import com.projectmodule.work.domain.TaskStatus;
import java.util.Map;

/**
 * A project's summary report: the number of active (non-archived) tasks in each status, with
 * every status present, including zero counts.
 */
public record ProjectSummaryReportResponse(Map<TaskStatus, Long> taskCountByStatus) {
}
