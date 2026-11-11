package com.projectmodule.collaboration.api;

import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A project's history, newest first, for anyone who can see the project. */
@RestController
public class ActivityController {

    public record ActivityResponse(UUID id, ActivityType type, ActorType actorType, UUID actorId, String summary,
                                   Instant occurredAt) {

        static ActivityResponse from(ProjectActivity activity) {
            return new ActivityResponse(activity.getId(), activity.getActivityType(), activity.getActorType(),
                    activity.getActorId().map(actor -> actor.value()).orElse(null), activity.getSummary(),
                    activity.getOccurredAt());
        }
    }

    private final ProjectActivityRepository activityRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final RequestContext requestContext;

    public ActivityController(ProjectActivityRepository activityRepository,
                              ProjectApplicationService projectApplicationService,
                              ProjectAuthorizationService projectAuthorizationService,
                              RequestContext requestContext) {
        this.activityRepository = activityRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/activity")
    public PageResponse<ActivityResponse> list(@PathVariable UUID projectId,
                                               @PageableDefault(size = 30) Pageable pageable) {
        projectApplicationService.findActiveProjectInCallerOrganization(requestContext, projectId);
        projectAuthorizationService.requirePermission(
                requestContext.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        Page<ProjectActivity> page = activityRepository.findByProjectIdOrderByOccurredAtDesc(projectId, pageable);
        return PageResponse.of(page, page.map(ActivityResponse::from).getContent());
    }
}
