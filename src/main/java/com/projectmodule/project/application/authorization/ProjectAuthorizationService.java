package com.projectmodule.project.application.authorization;

import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;

/**
 * The single point at which project-level permission is decided.
 *
 * <p>Authorization is enforced in the application layer rather than at the controller. Every
 * caller reaches the domain through this one gate, so a request arriving over HTTP, an AI tool
 * invocation and an automation run are all authorized identically. Enforcing at the controller
 * would leave the AI and automation paths unguarded.
 *
 * <p>This module decides only what a user may do <em>within a project</em>. Establishing who
 * the user is belongs to Authentication and User Management.
 *
 * <p>Interface only at this stage. The role-to-permission matrix and its implementation arrive
 * with the ProjectMember entity, which supplies the membership this contract reads.
 */
public interface ProjectAuthorizationService {

    /**
     * Whether the user holds the permission on the project.
     *
     * <p>For conditional behaviour, such as hiding an action a user cannot perform. Use
     * {@link #requirePermission} to guard an operation.
     */
    boolean hasPermission(ExternalUserId userId, UUID projectId, ProjectPermission permission);

    /**
     * Asserts that the user holds the permission on the project.
     *
     * @throws AuthorizationException if the permission is not held
     */
    void requirePermission(ExternalUserId userId, UUID projectId, ProjectPermission permission);
}
