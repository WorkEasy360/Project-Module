package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreateMilestoneRequest;
import com.projectmodule.work.api.dto.MilestoneResponse;
import com.projectmodule.work.api.dto.UpdateMilestoneRequest;
import com.projectmodule.work.application.MilestoneApplicationService;
import com.projectmodule.work.domain.Milestone;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for milestones, exactly as {@code docs/project/04-API.md} defines them: the
 * collection nested under a project, the item flat. Status transitions go through
 * {@code PATCH} — see {@link com.projectmodule.work.api.dto.UpdateMilestoneRequest}. Holds no
 * business logic or authorization decision; both are delegated to
 * {@link MilestoneApplicationService}.
 */
@RestController
public class MilestoneController {

    private final MilestoneApplicationService milestoneApplicationService;
    private final MilestoneMapper milestoneMapper;
    private final RequestContext requestContext;

    public MilestoneController(MilestoneApplicationService milestoneApplicationService,
                               MilestoneMapper milestoneMapper,
                               RequestContext requestContext) {
        this.milestoneApplicationService = milestoneApplicationService;
        this.milestoneMapper = milestoneMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/milestones")
    public ResponseEntity<MilestoneResponse> createMilestone(@PathVariable UUID projectId,
                                                              @Valid @RequestBody CreateMilestoneRequest request) {
        Milestone created = milestoneApplicationService.createMilestone(requestContext, projectId, request);
        MilestoneResponse response = milestoneMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/milestones/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/milestones")
    public List<MilestoneResponse> listMilestones(@PathVariable UUID projectId) {
        return milestoneApplicationService.listMilestones(requestContext, projectId).stream()
                .map(milestoneMapper::toResponse)
                .toList();
    }

    @PatchMapping("/milestones/{id}")
    public MilestoneResponse updateMilestone(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateMilestoneRequest request) {
        Milestone updated = milestoneApplicationService.updateMilestone(requestContext, id, request);
        return milestoneMapper.toResponse(updated);
    }

    /** Soft-archives the milestone. See {@link Milestone#archive}. */
    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<Void> archiveMilestone(@PathVariable UUID id) {
        milestoneApplicationService.archiveMilestone(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
