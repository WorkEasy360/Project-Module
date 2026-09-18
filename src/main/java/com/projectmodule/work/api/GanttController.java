package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.GanttResponse;
import com.projectmodule.work.application.GanttApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's Gantt view, per {@code docs/project/19-GANTT-SPEC.md}. Holds no
 * business logic or authorization decision; both are delegated to {@link GanttApplicationService}.
 */
@RestController
public class GanttController {

    private final GanttApplicationService ganttApplicationService;
    private final RequestContext requestContext;

    public GanttController(GanttApplicationService ganttApplicationService, RequestContext requestContext) {
        this.ganttApplicationService = ganttApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/gantt")
    public GanttResponse getGantt(@PathVariable UUID projectId) {
        return ganttApplicationService.getGantt(requestContext, projectId);
    }
}
