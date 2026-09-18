package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.DelayedItemsResponse;
import com.projectmodule.work.application.DelayDetectionApplicationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's delayed items, per
 * {@code docs/project/22-DELAY-DETECTION-SPEC.md}. Holds no business logic or authorization
 * decision; both are delegated to {@link DelayDetectionApplicationService}.
 */
@RestController
public class DelayDetectionController {

    private final DelayDetectionApplicationService delayDetectionApplicationService;
    private final RequestContext requestContext;

    public DelayDetectionController(DelayDetectionApplicationService delayDetectionApplicationService,
                                    RequestContext requestContext) {
        this.delayDetectionApplicationService = delayDetectionApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/delayed")
    public DelayedItemsResponse getDelayedItems(@PathVariable UUID projectId) {
        return new DelayedItemsResponse(delayDetectionApplicationService.getDelayedItems(requestContext, projectId));
    }
}
