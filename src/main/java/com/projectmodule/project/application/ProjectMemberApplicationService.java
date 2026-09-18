package com.projectmodule.project.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.ChangeMemberRoleRequest;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for project membership: list, add, change role, remove.
 *
 * <p>Every operation first resolves the project through
 * {@link ProjectApplicationService#findActiveProjectInCallerOrganization}, the same tenant and
 * existence check the project CRUD endpoints use, then applies project-level authorization
 * through {@link ProjectAuthorizationService}. Membership rules are enforced by calling
 * {@link ProjectMember} behaviour methods, never by setting fields directly.
 *
 * <p>The {@code OWNER} role is deliberately out of reach of every method here: it is established
 * once, at project creation (see {@link ProjectApplicationService#createProject}), and
 * reassigning or removing it is ownership transfer — a distinct operation this slice does not
 * implement. Requests that would assign, change, or remove {@code OWNER} are rejected with
 * {@link BusinessRuleViolationException} rather than silently allowed or silently ignored.
 */
@Service
public class ProjectMemberApplicationService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectMemberApplicationService(ProjectMemberRepository projectMemberRepository,
                                           ProjectApplicationService projectApplicationService,
                                           ProjectAuthorizationService projectAuthorizationService) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> listMembers(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        return projectMemberRepository.findByProjectId(projectId);
    }

    @Transactional
    public ProjectMember addMember(RequestContext context, UUID projectId, AddMemberRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.MANAGE_MEMBERS);

        if (request.role() == ProjectRole.OWNER) {
            throw new BusinessRuleViolationException(
                    "Cannot add a member as OWNER; ownership is established at project creation "
                            + "and reassigned only through ownership transfer, which is not yet supported");
        }

        ExternalUserId newMemberId = ExternalUserId.of(request.userId());
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, newMemberId.value())) {
            throw new ConflictException(
                    "User " + newMemberId + " is already a member of project " + projectId);
        }

        ProjectMember member = ProjectMember.join(projectId, newMemberId, request.role());
        return projectMemberRepository.save(member);
    }

    @Transactional
    public ProjectMember changeMemberRole(RequestContext context,
                                          UUID projectId,
                                          UUID memberId,
                                          ChangeMemberRoleRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.MANAGE_MEMBERS);

        ProjectMember member = findMemberInProject(projectId, memberId);

        if (member.getRole() == ProjectRole.OWNER || request.role() == ProjectRole.OWNER) {
            throw new BusinessRuleViolationException(
                    "Cannot change the OWNER role through member management; "
                            + "ownership transfer is not yet supported");
        }

        member.changeRole(request.role());
        return projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(RequestContext context, UUID projectId, UUID memberId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.MANAGE_MEMBERS);

        ProjectMember member = findMemberInProject(projectId, memberId);

        if (member.getRole() == ProjectRole.OWNER) {
            throw new BusinessRuleViolationException(
                    "Cannot remove the project OWNER; ownership transfer is not yet supported");
        }

        projectMemberRepository.delete(member);
    }

    private ProjectMember findMemberInProject(UUID projectId, UUID memberId) {
        ProjectMember member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", memberId));

        if (!member.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("ProjectMember", memberId);
        }

        return member;
    }
}
