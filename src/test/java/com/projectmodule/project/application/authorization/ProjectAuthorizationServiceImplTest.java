package com.projectmodule.project.application.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private ProjectAuthorizationServiceImpl authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new ProjectAuthorizationServiceImpl(projectMemberRepository);
    }

    private void givenMembership(ProjectRole role) {
        ProjectMember member = ProjectMember.join(PROJECT_ID, USER, role);
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER.value()))
                .thenReturn(Optional.of(member));
    }

    @Nested
    class Viewer {

        @Test
        @DisplayName("VIEWER may view but nothing else")
        void viewerHasViewOnly() {
            givenMembership(ProjectRole.VIEWER);

            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.VIEW_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.EDIT_PROJECT))
                    .isFalse();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.ARCHIVE_PROJECT))
                    .isFalse();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.MANAGE_MEMBERS))
                    .isFalse();
        }
    }

    @Nested
    class Member {

        @Test
        @DisplayName("MEMBER may view but not edit, archive or manage members")
        void memberHasViewOnly() {
            givenMembership(ProjectRole.MEMBER);

            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.VIEW_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.EDIT_PROJECT))
                    .isFalse();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.MANAGE_MEMBERS))
                    .isFalse();
        }
    }

    @Nested
    class Manager {

        @Test
        @DisplayName("MANAGER may view, edit and manage members, but not archive")
        void managerCannotArchive() {
            givenMembership(ProjectRole.MANAGER);

            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.VIEW_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.EDIT_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.MANAGE_MEMBERS))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.ARCHIVE_PROJECT))
                    .isFalse();
        }
    }

    @Nested
    class Owner {

        @Test
        @DisplayName("OWNER holds every CRUD-relevant permission, including archive")
        void ownerHasFullControl() {
            givenMembership(ProjectRole.OWNER);

            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.VIEW_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.EDIT_PROJECT))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.MANAGE_MEMBERS))
                    .isTrue();
            assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.ARCHIVE_PROJECT))
                    .isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(ProjectRole.class)
    @DisplayName("MANAGE_SETTINGS is granted to no role: fail-closed, not yet designed")
    void manageSettingsIsUngrantedForEveryRole(ProjectRole role) {
        givenMembership(role);

        assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.MANAGE_SETTINGS))
                .isFalse();
    }

    @Test
    @DisplayName("a caller with no membership row holds no permission at all")
    void noMembershipMeansNoPermission() {
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER.value()))
                .thenReturn(Optional.empty());

        assertThat(authorizationService.hasPermission(USER, PROJECT_ID, ProjectPermission.VIEW_PROJECT))
                .isFalse();
    }

    @Test
    @DisplayName("requirePermission throws AuthorizationException, not a generic exception")
    void requirePermissionThrowsOnDenial() {
        givenMembership(ProjectRole.VIEWER);

        assertThatThrownBy(() -> authorizationService.requirePermission(
                USER, PROJECT_ID, ProjectPermission.EDIT_PROJECT))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("requirePermission does not throw when the permission is held")
    void requirePermissionSucceedsWhenGranted() {
        givenMembership(ProjectRole.OWNER);

        authorizationService.requirePermission(USER, PROJECT_ID, ProjectPermission.ARCHIVE_PROJECT);
        // No exception: success.
    }
}
