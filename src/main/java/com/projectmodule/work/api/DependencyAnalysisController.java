package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.application.DependencyAnalysisApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's dependency analysis, per
 * {@code docs/project/23-DEPENDENCY-ANALYSIS-SPEC.md}. Holds no business logic or authorization
 * decision; both are delegated to {@link DependencyAnalysisApplicationService}.
 */
@RestController
public class DependencyAnalysisController {

    private final DependencyAnalysisApplicationService dependencyAnalysisApplicationService;
    private final RequestContext requestContext;

    public DependencyAnalysisController(DependencyAnalysisApplicationService dependencyAnalysisApplicationService,
                                        RequestContext requestContext) {
        this.dependencyAnalysisApplicationService = dependencyAnalysisApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/dependencies/analysis")
    public DependencyAnalysisResponse getAnalysis(@PathVariable UUID projectId) {
        return dependencyAnalysisApplicationService.getAnalysis(requestContext, projectId);
    }
}
