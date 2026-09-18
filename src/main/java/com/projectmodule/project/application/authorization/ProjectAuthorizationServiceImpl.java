package com.projectmodule.project.application.authorization;

import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decides project-level permission by reading the caller's {@code ProjectMember} row.
 *
 * <p>The role-to-permission matrix below is explicit, in-code data — not annotations, not
 * Spring Security, not AOP. It is derived directly from the capabilities already documented on
 * {@link ProjectRole}, since no separate matrix existed as data before this class:
 *
 * <ul>
 *   <li>{@code VIEWER}: "read-only access" -&gt; {@link ProjectPermission#VIEW_PROJECT} only.
 *   <li>{@code MEMBER}: "contributes work" (tasks, not yet built) -&gt; view only at the
 *       project level; nothing in {@code ProjectRole} attributes project editing to MEMBER.
 *   <li>{@code MANAGER}: "plans and runs the project, manages membership, cannot delete it"
 *       -&gt; view, edit, manage members; explicitly not archive.
 *   <li>{@code OWNER}: "full control, including deletion and ownership transfer" -&gt;
 *       everything MANAGER has, plus archive.
 * </ul>
 *
 * <p>{@link ProjectPermission#MANAGE_SETTINGS} is deliberately granted to no role: nothing in
 * the domain currently distinguishes a "settings" concept from general project editing, and
 * this permission has no caller in this slice. Leaving it unmapped is a fail-closed default,
 * not a guess at what it should eventually mean.
 *
 * <p>A caller with no {@code ProjectMember} row for the project holds no permissions at all,
 * whether or not the project is in their organization — that distinction is tenant scoping,
 * enforced separately before this class is ever consulted.
 */
@Service
public class ProjectAuthorizationServiceImpl implements ProjectAuthorizationService {

    private static final Map<ProjectRole, Set<ProjectPermission>> ROLE_PERMISSIONS =
            new EnumMap<>(ProjectRole.class);

    static {
        ROLE_PERMISSIONS.put(ProjectRole.VIEWER, EnumSet.of(
                ProjectPermission.VIEW_PROJECT));

        ROLE_PERMISSIONS.put(ProjectRole.MEMBER, EnumSet.of(
                ProjectPermission.VIEW_PROJECT));

        ROLE_PERMISSIONS.put(ProjectRole.MANAGER, EnumSet.of(
                ProjectPermission.VIEW_PROJECT,
                ProjectPermission.EDIT_PROJECT,
                ProjectPermission.MANAGE_MEMBERS));

        ROLE_PERMISSIONS.put(ProjectRole.OWNER, EnumSet.of(
                ProjectPermission.VIEW_PROJECT,
                ProjectPermission.EDIT_PROJECT,
                ProjectPermission.MANAGE_MEMBERS,
                ProjectPermission.ARCHIVE_PROJECT));
    }

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAuthorizationServiceImpl(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    @Override
    public boolean hasPermission(ExternalUserId userId, UUID projectId, ProjectPermission permission) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId.value())
                .map(member -> ROLE_PERMISSIONS.getOrDefault(member.getRole(), Set.of())
                        .contains(permission))
                .orElse(false);
    }

    @Override
    public void requirePermission(ExternalUserId userId, UUID projectId, ProjectPermission permission) {
        if (!hasPermission(userId, projectId, permission)) {
            throw new AuthorizationException(
                    "User " + userId + " does not have permission " + permission
                            + " on project " + projectId);
        }
    }
}
