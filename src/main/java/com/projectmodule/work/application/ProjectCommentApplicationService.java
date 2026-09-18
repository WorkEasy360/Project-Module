package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateCommentRequest;
import com.projectmodule.work.api.dto.UpdateCommentRequest;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.infrastructure.ProjectCommentRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for project comment CRUD.
 *
 * <p>The owning project is resolved through
 * {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, the same tenant
 * boundary and existence check every entity uses.
 *
 * <p>Authorization here is not purely permission-based, unlike every other entity in this
 * module: per the approved specification ({@code docs/project/10-COMMENT-SPEC.md}), creating and
 * listing comments require {@code VIEW_PROJECT}, editing is restricted to the comment's own
 * author regardless of permission, and archiving is allowed for the author <em>or</em> a caller
 * holding {@code EDIT_PROJECT} (moderation). No new permission is added to the existing five —
 * the author check is an identity comparison ({@link ProjectComment#isAuthoredBy}), not a
 * permission.
 *
 * <p>Like {@link IssueApplicationService}/{@link DecisionApplicationService}, this has no
 * {@link WorkEventRecorder} dependency: the approved specification defines no Comment events, so
 * there is nothing to record. Comment mutations do not appear in the outbox or the project
 * activity audit trail.
 */
@Service
public class ProjectCommentApplicationService {

    private final ProjectCommentRepository commentRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectCommentApplicationService(ProjectCommentRepository commentRepository,
                                            ProjectApplicationService projectApplicationService,
                                            ProjectAuthorizationService projectAuthorizationService) {
        this.commentRepository = commentRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public ProjectComment createComment(RequestContext context, UUID projectId, CreateCommentRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        ExternalUserId authorId = context.requireUserId();
        projectAuthorizationService.requirePermission(authorId, projectId, ProjectPermission.VIEW_PROJECT);

        ProjectComment comment = ProjectComment.create(projectId, authorId, request.body());

        return commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public Page<ProjectComment> listComments(RequestContext context, UUID projectId, Pageable pageable) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return commentRepository.findByProjectIdAndArchivedAtIsNull(projectId, pageable);
    }

    @Transactional
    public ProjectComment updateComment(RequestContext context, UUID commentId, UpdateCommentRequest request) {
        ProjectComment comment = findActiveComment(commentId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, comment.getProjectId());

        ExternalUserId caller = context.requireUserId();
        if (!comment.isAuthoredBy(caller)) {
            throw new AuthorizationException("Only the comment's author may edit it");
        }

        if (comment.getVersion() != request.version()) {
            throw new ConflictException(
                    "Comment " + commentId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        comment.edit(request.body());

        return commentRepository.save(comment);
    }

    @Transactional
    public void archiveComment(RequestContext context, UUID commentId) {
        ProjectComment comment = findActiveComment(commentId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, comment.getProjectId());

        ExternalUserId caller = context.requireUserId();
        boolean isModerator = projectAuthorizationService.hasPermission(
                caller, comment.getProjectId(), ProjectPermission.EDIT_PROJECT);
        if (!comment.isAuthoredBy(caller) && !isModerator) {
            throw new AuthorizationException(
                    "Only the comment's author or a caller with EDIT_PROJECT may archive it");
        }

        comment.archive(caller);
        commentRepository.save(comment);
    }

    /**
     * Finds a comment by id. Unlike {@code ProjectApplicationService}'s equivalent, this does
     * not also check the caller's organization: {@code PATCH}/{@code DELETE /comments/:id}
     * carry no project id, so the owning project is not known until after this lookup.
     */
    private ProjectComment findActiveComment(UUID commentId) {
        return commentRepository.findByIdAndArchivedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }
}
