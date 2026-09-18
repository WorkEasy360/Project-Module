package com.projectmodule.project.api;

import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.ChangeMemberRoleRequest;
import com.projectmodule.project.api.dto.ProjectMemberResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.domain.ProjectMember;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for project membership, per {@code docs/project/04-API.md}.
 *
 * <p>The {@code /api/v1} prefix is applied centrally by
 * {@link com.projectmodule.config.ApiConfiguration}. Holds no business logic or authorization
 * decision; both are delegated to {@link ProjectMemberApplicationService}.
 */
@RestController
@RequestMapping("/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberApplicationService projectMemberApplicationService;
    private final ProjectMemberMapper projectMemberMapper;
    private final RequestContext requestContext;

    public ProjectMemberController(ProjectMemberApplicationService projectMemberApplicationService,
                                   ProjectMemberMapper projectMemberMapper,
                                   RequestContext requestContext) {
        this.projectMemberApplicationService = projectMemberApplicationService;
        this.projectMemberMapper = projectMemberMapper;
        this.requestContext = requestContext;
    }

    @GetMapping
    public List<ProjectMemberResponse> listMembers(@PathVariable UUID projectId) {
        return projectMemberApplicationService.listMembers(requestContext, projectId).stream()
                .map(projectMemberMapper::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable UUID projectId,
                                                            @Valid @RequestBody AddMemberRequest request) {
        ProjectMember created = projectMemberApplicationService.addMember(requestContext, projectId, request);
        ProjectMemberResponse response = projectMemberMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{memberId}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{memberId}")
    public ProjectMemberResponse changeMemberRole(@PathVariable UUID projectId,
                                                  @PathVariable UUID memberId,
                                                  @Valid @RequestBody ChangeMemberRoleRequest request) {
        ProjectMember updated = projectMemberApplicationService.changeMemberRole(
                requestContext, projectId, memberId, request);
        return projectMemberMapper.toResponse(updated);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        projectMemberApplicationService.removeMember(requestContext, projectId, memberId);
        return ResponseEntity.noContent().build();
    }
}
