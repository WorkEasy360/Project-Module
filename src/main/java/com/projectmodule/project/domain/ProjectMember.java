package com.projectmodule.project.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's membership of one project, and the role they hold on it.
 *
 * <p>The project is referenced by identifier rather than by a JPA association. The database
 * enforces the relationship with a foreign key; keeping it out of the object model means
 * loading a member never cascades into loading a project, and the two aggregates stay
 * independently addressable.
 *
 * <p>The user is referenced by opaque identifier because the user record belongs to another
 * module.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ProjectRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** For JPA only. */
    protected ProjectMember() {
    }

    private ProjectMember(UUID id, UUID projectId, ExternalUserId userId, ProjectRole role) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.userId = Objects.requireNonNull(userId, "userId is required").value();
        this.role = Objects.requireNonNull(role, "role is required");
        this.joinedAt = Instant.now();
    }

    public static ProjectMember join(UUID projectId, ExternalUserId userId, ProjectRole role) {
        return new ProjectMember(UuidV7.generate(), projectId, userId, role);
    }

    /**
     * Changes the role held on this project.
     *
     * @throws BusinessRuleViolationException if the role is unchanged
     */
    public void changeRole(ProjectRole newRole) {
        Objects.requireNonNull(newRole, "role is required");
        if (this.role == newRole) {
            throw new BusinessRuleViolationException("Member already holds the role " + newRole);
        }
        this.role = newRole;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ExternalUserId getUserId() {
        return ExternalUserId.of(userId);
    }

    public ProjectRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
