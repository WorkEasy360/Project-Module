package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.application.ProjectHealthApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's health indicators, per
 * {@code docs/project/21-PROJECT-HEALTH-SPEC.md}. Holds no business logic or authorization
 * decision; both are delegated to {@link ProjectHealthApplicationService}.
 */
@RestController
public class ProjectHealthController {

    private final ProjectHealthApplicationService projectHealthApplicationService;
    private final RequestContext requestContext;

    public ProjectHealthController(ProjectHealthApplicationService projectHealthApplicationService,
                                   RequestContext requestContext) {
        this.projectHealthApplicationService = projectHealthApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/health")
    public ProjectHealthResponse getHealth(@PathVariable UUID projectId) {
        return projectHealthApplicationService.getHealth(requestContext, projectId);
    }
}
