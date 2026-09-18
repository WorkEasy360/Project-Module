package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A short, unthreaded remark left on a project.
 *
 * <p>Specified in {@code docs/project/10-COMMENT-SPEC.md} (approved), since
 * {@code docs/project/01-SPEC.md}/{@code 03-DATABASE.md} document {@code ProjectComment} only as
 * a bare entity name. Per the approved specification: comments attach only to a Project (not to
 * Task/Risk/Issue/Decision), there is no threading/replies, no status/lifecycle beyond
 * soft-archive, and no domain events — the approved spec defines none for Comment.
 *
 * <p>{@code authorId} is never supplied by a caller: it is always the identity that created the
 * comment ({@code RequestContext.requireUserId()}), the same opaque-reference convention
 * {@code Task.assigneeId}/{@code Decision.decidedBy} already use, but immutable — a comment
 * cannot be reattributed to someone else after the fact.
 */
@Entity
@Table(name = "project_comments")
public class ProjectComment extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** For JPA only. */
    protected ProjectComment() {
    }

    private ProjectComment(UUID id, UUID projectId, ExternalUserId authorId, String body) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.authorId = Objects.requireNonNull(authorId, "authorId is required").value();
        this.body = validateBody(body);
    }

    /**
     * Posts a comment.
     *
     * @throws ValidationException if the body is blank
     */
    public static ProjectComment create(UUID projectId, ExternalUserId authorId, String body) {
        return new ProjectComment(UuidV7.generate(), projectId, authorId, body);
    }

    /**
     * Replaces the comment's body.
     *
     * @throws ValidationException if the new body is blank
     */
    public void edit(String newBody) {
        requireNotArchived("edit");
        this.body = validateBody(newBody);
    }

    /**
     * Archives the comment. Deleting a comment means archiving it, consistent with
     * {@code Project}. Raises no event: the approved specification defines none for Comment.
     *
     * @throws BusinessRuleViolationException if it is already archived
     */
    public void archive(ExternalUserId archivedByUser) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Comment is already archived");
        }
        this.archivedBy = Objects.requireNonNull(archivedByUser, "archivedBy is required").value();
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Whether the given user is this comment's author. */
    public boolean isAuthoredBy(ExternalUserId userId) {
        return this.authorId.equals(Objects.requireNonNull(userId, "userId is required").value());
    }

    private void requireNotArchived(String action) {
        if (isArchived()) {
            throw new BusinessRuleViolationException("Cannot " + action + " an archived comment");
        }
    }

    private static String validateBody(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("Comment body must not be blank");
        }
        return candidate.trim();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ExternalUserId getAuthorId() {
        return ExternalUserId.of(authorId);
    }

    public String getBody() {
        return body;
    }

    public Optional<Instant> getArchivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Optional<ExternalUserId> getArchivedBy() {
        return Optional.ofNullable(archivedBy).map(ExternalUserId::of);
    }
}
