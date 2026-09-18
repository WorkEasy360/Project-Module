package com.projectmodule.automation.api;

import com.projectmodule.automation.api.dto.AutomationResponse;
import com.projectmodule.automation.api.dto.AutomationRunResponse;
import com.projectmodule.automation.api.dto.CreateAutomationRequest;
import com.projectmodule.automation.api.dto.UpdateAutomationRequest;
import com.projectmodule.automation.application.ProjectAutomationApplicationService;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.common.context.RequestContext;
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
 * REST endpoints for automation rules, per {@code docs/project/28-AUTOMATION-SPEC.md} §8. Holds
 * no business logic or authorization decision; both are delegated to
 * {@link ProjectAutomationApplicationService}.
 */
@RestController
public class ProjectAutomationController {

    private final ProjectAutomationApplicationService projectAutomationApplicationService;
    private final ProjectAutomationMapper projectAutomationMapper;
    private final RequestContext requestContext;

    public ProjectAutomationController(ProjectAutomationApplicationService projectAutomationApplicationService,
                                       ProjectAutomationMapper projectAutomationMapper,
                                       RequestContext requestContext) {
        this.projectAutomationApplicationService = projectAutomationApplicationService;
        this.projectAutomationMapper = projectAutomationMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/automations")
    public ResponseEntity<AutomationResponse> createAutomation(
            @PathVariable UUID projectId, @Valid @RequestBody CreateAutomationRequest request) {
        ProjectAutomation created = projectAutomationApplicationService.createAutomation(requestContext, projectId,
                request.name(), request.description(), request.triggerEvent(), request.actionType(),
                request.actionRecipientId(), request.actionChannelReference(), request.actionMessage());
        AutomationResponse response = projectAutomationMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/automations/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/automations")
    public List<AutomationResponse> listAutomations(@PathVariable UUID projectId) {
        return projectAutomationApplicationService.listAutomations(requestContext, projectId).stream()
                .map(projectAutomationMapper::toResponse)
                .toList();
    }

    @PatchMapping("/automations/{id}")
    public AutomationResponse updateAutomation(@PathVariable UUID id, @Valid @RequestBody UpdateAutomationRequest request) {
        ProjectAutomation updated = projectAutomationApplicationService.updateAutomation(requestContext, id,
                request.name(), request.description(), request.actionMessage(), request.enabled(),
                request.version());
        return projectAutomationMapper.toResponse(updated);
    }

    /** Soft-archives the automation. See {@link ProjectAutomation#archive}. */
    @DeleteMapping("/automations/{id}")
    public ResponseEntity<Void> archiveAutomation(@PathVariable UUID id) {
        projectAutomationApplicationService.archiveAutomation(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/automations/{id}/runs")
    public List<AutomationRunResponse> listRuns(@PathVariable UUID id) {
        return projectAutomationApplicationService.listRuns(requestContext, id).stream()
                .map(projectAutomationMapper::toResponse)
                .toList();
    }
}
