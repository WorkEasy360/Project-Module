package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.CommentResponse;
import com.projectmodule.work.domain.ProjectComment;
import org.springframework.stereotype.Component;

/** Maps {@link ProjectComment} to its API representation. Plain, hand-written mapping. */
@Component
public class CommentMapper {

    public CommentResponse toResponse(ProjectComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getProjectId(),
                comment.getAuthorId().value(),
                comment.getBody(),
                comment.isArchived(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getVersion());
    }
}
