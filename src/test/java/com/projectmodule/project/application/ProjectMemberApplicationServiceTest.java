package com.projectmodule.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.ChangeMemberRoleRequest;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectMemberApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId CALLER = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ProjectMemberApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberApplicationService(
                projectMemberRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(CALLER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists members after checking VIEW_PROJECT")
        void listsMembers() {
            ProjectMember member = ProjectMember.join(project.getId(), CALLER, ProjectRole.MEMBER);
            when(projectMemberRepository.findByProjectId(project.getId())).thenReturn(List.of(member));

            List<ProjectMember> result = service.listMembers(context, project.getId());

            assertThat(result).containsExactly(member);
            verify(projectAuthorizationService).requirePermission(
                    CALLER, project.getId(), ProjectPermission.VIEW_PROJECT);
        }

        @Test
        @DisplayName("denies listing without VIEW_PROJECT")
        void deniesListingWithoutPermission() {
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(CALLER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listMembers(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class Add {

        @Test
        @DisplayName("adds a member after checking MANAGE_MEMBERS")
        void addsMember() {
            UUID newUserId = UUID.randomUUID();
            when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), newUserId))
                    .thenReturn(false);
            when(projectMemberRepository.save(any(ProjectMember.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AddMemberRequest request = new AddMemberRequest(newUserId, ProjectRole.MEMBER);
            ProjectMember added = service.addMember(context, project.getId(), request);

            assertThat(added.getUserId()).isEqualTo(ExternalUserId.of(newUserId));
            assertThat(added.getRole()).isEqualTo(ProjectRole.MEMBER);
            verify(projectAuthorizationService).requirePermission(
                    CALLER, project.getId(), ProjectPermission.MANAGE_MEMBERS);
        }

        @Test
        @DisplayName("rejects adding a member as OWNER")
        void rejectsAddingOwner() {
            AddMemberRequest request = new AddMemberRequest(UUID.randomUUID(), ProjectRole.OWNER);

            assertThatThrownBy(() -> service.addMember(context, project.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(projectMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a duplicate membership using the existing uniqueness check")
        void rejectsDuplicateMembership() {
            UUID existingUserId = UUID.randomUUID();
            when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), existingUserId))
                    .thenReturn(true);

            AddMemberRequest request = new AddMemberRequest(existingUserId, ProjectRole.VIEWER);

            assertThatThrownBy(() -> service.addMember(context, project.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(projectMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("denies adding a member without MANAGE_MEMBERS")
        void deniesAddWithoutPermission() {
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(CALLER, project.getId(), ProjectPermission.MANAGE_MEMBERS);

            AddMemberRequest request = new AddMemberRequest(UUID.randomUUID(), ProjectRole.MEMBER);

            assertThatThrownBy(() -> service.addMember(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class ChangeRole {

        @Test
        @DisplayName("changes a non-owner member's role")
        void changesRole() {
            ProjectMember member = ProjectMember.join(project.getId(), CALLER, ProjectRole.VIEWER);
            when(projectMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
            when(projectMemberRepository.save(any(ProjectMember.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ChangeMemberRoleRequest request = new ChangeMemberRoleRequest(ProjectRole.MANAGER);
            ProjectMember updated = service.changeMemberRole(
                    context, project.getId(), member.getId(), request);

            assertThat(updated.getRole()).isEqualTo(ProjectRole.MANAGER);
        }

        @Test
        @DisplayName("rejects assigning the OWNER role via member management")
        void rejectsAssigningOwner() {
            ProjectMember member = ProjectMember.join(project.getId(), CALLER, ProjectRole.MEMBER);
            when(projectMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

            ChangeMemberRoleRequest request = new ChangeMemberRoleRequest(ProjectRole.OWNER);

            assertThatThrownBy(() -> service.changeMemberRole(
                    context, project.getId(), member.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects changing the current OWNER's role")
        void rejectsChangingExistingOwner() {
            ProjectMember ownerMembership = ProjectMember.join(project.getId(), OWNER, ProjectRole.OWNER);
            when(projectMemberRepository.findById(ownerMembership.getId()))
                    .thenReturn(Optional.of(ownerMembership));

            ChangeMemberRoleRequest request = new ChangeMemberRoleRequest(ProjectRole.MANAGER);

            assertThatThrownBy(() -> service.changeMemberRole(
                    context, project.getId(), ownerMembership.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("reports not found for a member id belonging to a different project")
        void reportsNotFoundForCrossProjectMember() {
            UUID otherProjectId = UUID.randomUUID();
            ProjectMember foreignMember = ProjectMember.join(otherProjectId, CALLER, ProjectRole.MEMBER);
            when(projectMemberRepository.findById(foreignMember.getId()))
                    .thenReturn(Optional.of(foreignMember));

            ChangeMemberRoleRequest request = new ChangeMemberRoleRequest(ProjectRole.MANAGER);

            assertThatThrownBy(() -> service.changeMemberRole(
                    context, project.getId(), foreignMember.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Remove {

        @Test
        @DisplayName("removes a non-owner member")
        void removesMember() {
            ProjectMember member = ProjectMember.join(project.getId(), CALLER, ProjectRole.MEMBER);
            when(projectMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

            service.removeMember(context, project.getId(), member.getId());

            verify(projectMemberRepository).delete(member);
        }

        @Test
        @DisplayName("rejects removing the OWNER")
        void rejectsRemovingOwner() {
            ProjectMember ownerMembership = ProjectMember.join(project.getId(), OWNER, ProjectRole.OWNER);
            when(projectMemberRepository.findById(ownerMembership.getId()))
                    .thenReturn(Optional.of(ownerMembership));

            assertThatThrownBy(() -> service.removeMember(context, project.getId(), ownerMembership.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        @DisplayName("denies removal without MANAGE_MEMBERS")
        void deniesRemovalWithoutPermission() {
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(CALLER, project.getId(), ProjectPermission.MANAGE_MEMBERS);

            assertThatThrownBy(() -> service.removeMember(context, project.getId(), UUID.randomUUID()))
                    .isInstanceOf(AuthorizationException.class);
        }
    }
}
