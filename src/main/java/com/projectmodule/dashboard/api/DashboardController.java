package com.projectmodule.dashboard.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.dashboard.api.dto.DashboardResponse;
import com.projectmodule.dashboard.application.DashboardApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the P0 basic dashboard (see {@code docs/project/08-ROADMAP.md}).
 *
 * <p>The {@code /api/v1} prefix is applied centrally by
 * {@link com.projectmodule.config.ApiConfiguration}. Holds no logic of its own; delegates to
 * {@link DashboardApplicationService}.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardApplicationService dashboardApplicationService;
    private final RequestContext requestContext;

    public DashboardController(DashboardApplicationService dashboardApplicationService,
                               RequestContext requestContext) {
        this.dashboardApplicationService = dashboardApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public DashboardResponse getDashboard() {
        return dashboardApplicationService.getDashboard(requestContext);
    }
}
