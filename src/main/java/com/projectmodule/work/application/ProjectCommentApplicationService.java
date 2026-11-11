package com.projectmodule.work.application;

import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
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
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.ProjectCommentRepository;
import java.util.List;
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
 * <p>The approved specification defines no Comment events, so nothing goes to the outbox. A new
 * comment is added to the project's activity feed, and tells whoever should hear about it.
 */
@Service
public class ProjectCommentApplicationService {

    private final ProjectCommentRepository commentRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final TaskApplicationService taskApplicationService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final TaskNotifier taskNotifier;

    public ProjectCommentApplicationService(ProjectCommentRepository commentRepository,
                                            ProjectApplicationService projectApplicationService,
                                            ProjectAuthorizationService projectAuthorizationService,
                                            TaskApplicationService taskApplicationService,
                                            ProjectMemberRepository projectMemberRepository,
                                            ProjectActivityRepository projectActivityRepository,
                                            TaskNotifier taskNotifier) {
        this.commentRepository = commentRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.taskApplicationService = taskApplicationService;
        this.projectMemberRepository = projectMemberRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.taskNotifier = taskNotifier;
    }

    /** Comments on a task, oldest first. */
    @Transactional(readOnly = true)
    public List<ProjectComment> listTaskComments(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.getTask(context, taskId);
        return commentRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId());
    }

    /** Posts a comment on a task and tells its followers and anyone mentioned. */
    @Transactional
    public ProjectComment createTaskComment(RequestContext context, UUID taskId, CreateCommentRequest request) {
        Task task = taskApplicationService.getTask(context, taskId);
        ExternalUserId authorId = context.requireUserId();

        ProjectComment comment = commentRepository.save(
                ProjectComment.createOnTask(task.getProjectId(), task.getId(), authorId, request.body()));
        projectActivityRepository.save(ProjectActivity.record(task.getProjectId(), ActivityType.COMMENT_ADDED, context,
                "Commented on task \"" + task.getName() + "\"", null));
        taskNotifier.commented(task, authorId.value(), onlyProjectMembers(task.getProjectId(), request.mentions()));
        return comment;
    }

    /** Only people on the project can be told about it, whatever a request names. */
    private List<UUID> onlyProjectMembers(UUID projectId, List<UUID> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        return mentions.stream()
                .filter(person -> projectMemberRepository.existsByProjectIdAndUserId(projectId, person))
                .toList();
    }

    @Transactional
    public ProjectComment createComment(RequestContext context, UUID projectId, CreateCommentRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        ExternalUserId authorId = context.requireUserId();
        projectAuthorizationService.requirePermission(authorId, projectId, ProjectPermission.VIEW_PROJECT);

        ProjectComment comment = commentRepository.save(ProjectComment.create(projectId, authorId, request.body()));
        projectActivityRepository.save(ProjectActivity.record(projectId, ActivityType.COMMENT_ADDED, context,
                "Commented on the project", null));
        taskNotifier.mentionedInProject(projectId, authorId.value(), onlyProjectMembers(projectId, request.mentions()));
        return comment;
    }

    @Transactional(readOnly = true)
    public Page<ProjectComment> listComments(RequestContext context, UUID projectId, Pageable pageable) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return commentRepository.findProjectLevelComments(projectId, pageable);
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
