package com.projectmodule.customization.api;

import com.projectmodule.customization.api.dto.TemplateResponse;
import com.projectmodule.customization.domain.ProjectTemplate;
import org.springframework.stereotype.Component;

/** Maps {@link ProjectTemplate} to its API representation. Plain, hand-written mapping. */
@Component
public class TemplateMapper {

    public TemplateResponse toResponse(ProjectTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getOrganizationId().value(),
                template.getName(),
                template.getDescription().orElse(null),
                template.getDefaultPriority().orElse(null),
                template.isArchived(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getVersion());
    }
}
