package com.projectmodule.work.api;

import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CommentResponse;
import com.projectmodule.work.api.dto.CreateCommentRequest;
import com.projectmodule.work.api.dto.UpdateCommentRequest;
import com.projectmodule.work.application.ProjectCommentApplicationService;
import com.projectmodule.work.domain.ProjectComment;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for project comments, per {@code docs/project/10-COMMENT-SPEC.md} (approved) —
 * the "collection nested under a project, item flat" pattern Phase/Milestone/Risk/Issue/Decision
 * already use, with no single-item {@code GET}. Unlike those, listing is paginated
 * ({@link PageResponse}/{@link Pageable}), reusing the same convention
 * {@link com.projectmodule.project.api.ProjectController#listProjects} already establishes,
 * ordered oldest-first by default. Holds no business logic or authorization decision; both are
 * delegated to {@link ProjectCommentApplicationService}.
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

    @PostMapping("/projects/{projectId}/comments")
    public ResponseEntity<CommentResponse> createComment(@PathVariable UUID projectId,
                                                          @Valid @RequestBody CreateCommentRequest request) {
        ProjectComment created = commentApplicationService.createComment(requestContext, projectId, request);
        CommentResponse response = commentMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/comments/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/comments")
    public PageResponse<CommentResponse> listComments(
            @PathVariable UUID projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ProjectComment> page = commentApplicationService.listComments(requestContext, projectId, pageable);
        return PageResponse.of(page, page.map(commentMapper::toResponse).getContent());
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
