package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.MilestoneResponse;
import com.projectmodule.work.api.dto.PhaseResponse;
import com.projectmodule.work.api.dto.TaskResponse;
import com.projectmodule.work.api.dto.TimelinePhaseResponse;
import com.projectmodule.work.api.dto.TimelineResponse;
import com.projectmodule.work.application.TimelineApplicationService;
import com.projectmodule.work.application.TimelinePhaseGroup;
import com.projectmodule.work.application.TimelineResult;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for a project's timeline, per {@code docs/project/18-TIMELINE-SPEC.md}. Holds no
 * business logic or authorization decision; both are delegated to
 * {@link TimelineApplicationService}.
 */
@RestController
public class TimelineController {

    private final TimelineApplicationService timelineApplicationService;
    private final PhaseMapper phaseMapper;
    private final MilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final RequestContext requestContext;

    public TimelineController(TimelineApplicationService timelineApplicationService,
                              PhaseMapper phaseMapper,
                              MilestoneMapper milestoneMapper,
                              TaskMapper taskMapper,
                              RequestContext requestContext) {
        this.timelineApplicationService = timelineApplicationService;
        this.phaseMapper = phaseMapper;
        this.milestoneMapper = milestoneMapper;
        this.taskMapper = taskMapper;
        this.requestContext = requestContext;
    }

    @GetMapping("/projects/{projectId}/timeline")
    public TimelineResponse getTimeline(@PathVariable UUID projectId) {
        TimelineResult result = timelineApplicationService.getTimeline(requestContext, projectId);

        List<TimelinePhaseResponse> phases = result.phases().stream()
                .map(this::toPhaseResponse)
                .toList();
        List<TaskResponse> tasks = result.tasks().stream()
                .map(taskMapper::toResponse)
                .toList();

        return new TimelineResponse(phases, tasks);
    }

    private TimelinePhaseResponse toPhaseResponse(TimelinePhaseGroup group) {
        PhaseResponse phase = phaseMapper.toResponse(group.phase());
        List<MilestoneResponse> milestones = group.milestones().stream()
                .map(milestoneMapper::toResponse)
                .toList();
        return new TimelinePhaseResponse(phase.id(), phase.name(), phase.startDate(), phase.endDate(), milestones);
    }
}
