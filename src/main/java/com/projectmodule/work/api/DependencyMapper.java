package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.DependencyResponse;
import com.projectmodule.work.domain.Dependency;
import org.springframework.stereotype.Component;

/** Maps {@link Dependency} to its API representation. Plain, hand-written mapping. */
@Component
public class DependencyMapper {

    public DependencyResponse toResponse(Dependency dependency) {
        return new DependencyResponse(
                dependency.getId(),
                dependency.getDependentTaskId(),
                dependency.getPrerequisiteTaskId(),
                dependency.isBroken(),
                dependency.isArchived(),
                dependency.getCreatedAt(),
                dependency.getUpdatedAt(),
                dependency.getVersion());
    }
}
