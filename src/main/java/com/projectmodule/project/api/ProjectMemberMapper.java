package com.projectmodule.project.api;

import com.projectmodule.project.api.dto.ProjectMemberResponse;
import com.projectmodule.project.domain.ProjectMember;
import org.springframework.stereotype.Component;

/** Maps {@link ProjectMember} to its API representation. Plain, hand-written mapping. */
@Component
public class ProjectMemberMapper {

    public ProjectMemberResponse toResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getProjectId(),
                member.getUserId().value(),
                member.getRole(),
                member.getJoinedAt(),
                member.getVersion());
    }
}
