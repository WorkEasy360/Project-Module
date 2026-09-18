package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.IssueResponse;
import com.projectmodule.work.domain.Issue;
import org.springframework.stereotype.Component;

/** Maps {@link Issue} to its API representation. Plain, hand-written mapping. */
@Component
public class IssueMapper {

    public IssueResponse toResponse(Issue issue) {
        return new IssueResponse(
                issue.getId(),
                issue.getProjectId(),
                issue.getName(),
                issue.getDescription().orElse(null),
                issue.getPriority(),
                issue.isArchived(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                issue.getVersion());
    }
}
