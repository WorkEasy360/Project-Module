package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.application.ReportsApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's summary report, per {@code docs/project/24-REPORTS-SPEC.md}.
 * Holds no business logic or authorization decision; both are delegated to
 * {@link ReportsApplicationService}.
 */
@RestController
public class ReportsController {

    private final ReportsApplicationService reportsApplicationService;
    private final RequestContext requestContext;

    public ReportsController(ReportsApplicationService reportsApplicationService, RequestContext requestContext) {
        this.reportsApplicationService = reportsApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/reports/summary")
    public ProjectSummaryReportResponse getSummary(@PathVariable UUID projectId) {
        return reportsApplicationService.getSummary(requestContext, projectId);
    }
}
