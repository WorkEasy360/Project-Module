package com.projectmodule.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectMemberTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Test
    @DisplayName("joining records the project, user, role and moment")
    void recordsMembership() {
        ProjectMember member = ProjectMember.join(PROJECT_ID, USER, ProjectRole.MEMBER);

        assertThat(member.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(member.getUserId()).isEqualTo(USER);
        assertThat(member.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(member.getJoinedAt()).isNotNull();
        assertThat(member.getId()).isNotNull();
    }

    @Test
    @DisplayName("supports all four approved roles")
    void supportsApprovedRoles() {
        assertThat(ProjectRole.values())
                .containsExactly(ProjectRole.OWNER, ProjectRole.MANAGER,
                        ProjectRole.MEMBER, ProjectRole.VIEWER);
    }

    @Test
    @DisplayName("changes role")
    void changesRole() {
        ProjectMember member = ProjectMember.join(PROJECT_ID, USER, ProjectRole.VIEWER);

        member.changeRole(ProjectRole.MANAGER);

        assertThat(member.getRole()).isEqualTo(ProjectRole.MANAGER);
    }

    @Test
    @DisplayName("rejects a no-op role change rather than writing a misleading audit entry")
    void rejectsRedundantRoleChange() {
        ProjectMember member = ProjectMember.join(PROJECT_ID, USER, ProjectRole.MANAGER);

        assertThatThrownBy(() -> member.changeRole(ProjectRole.MANAGER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("rejects a null role")
    void rejectsNullRole() {
        ProjectMember member = ProjectMember.join(PROJECT_ID, USER, ProjectRole.MEMBER);

        assertThatThrownBy(() -> member.changeRole(null))
                .isInstanceOf(NullPointerException.class);
    }
}
