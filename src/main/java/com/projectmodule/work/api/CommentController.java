package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CommentResponse;
import com.projectmodule.work.api.dto.CreateCommentRequest;
import com.projectmodule.work.api.dto.UpdateCommentRequest;
import com.projectmodule.work.application.ProjectCommentApplicationService;
import com.projectmodule.work.domain.ProjectComment;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for task comments: list and post under a task, edit and archive by comment id.
 * Holds no business logic or authorization decision; both are delegated to
 * {@link ProjectCommentApplicationService}.
 */
@RestController
public class CommentController {

    private final ProjectCommentApplicationService commentApplicationService;
    private final CommentMapper commentMapper;
    private final RequestContext requestContext;

    public CommentController(ProjectCommentApplicationService commentApplicationService,
                             CommentMapper commentMapper,
                             RequestContext requestContext) {
        this.commentApplicationService = commentApplicationService;
        this.commentMapper = commentMapper;
        this.requestContext = requestContext;
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentResponse> listTaskComments(@PathVariable UUID taskId) {
        return commentApplicationService.listTaskComments(requestContext, taskId).stream()
                .map(commentMapper::toResponse).toList();
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createTaskComment(@PathVariable UUID taskId,
                                             @Valid @RequestBody CreateCommentRequest request) {
        return commentMapper.toResponse(commentApplicationService.createTaskComment(requestContext, taskId, request));
    }

    @PatchMapping("/comments/{id}")
    public CommentResponse updateComment(@PathVariable UUID id, @Valid @RequestBody UpdateCommentRequest request) {
        ProjectComment updated = commentApplicationService.updateComment(requestContext, id, request);
        return commentMapper.toResponse(updated);
    }

    /** Soft-archives the comment. See {@link ProjectComment#archive}. */
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> archiveComment(@PathVariable UUID id) {
        commentApplicationService.archiveComment(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
