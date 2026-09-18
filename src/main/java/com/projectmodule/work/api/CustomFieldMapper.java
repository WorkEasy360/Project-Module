package com.projectmodule.work.api;

import com.projectmodule.work.api.dto.CustomFieldResponse;
import com.projectmodule.work.domain.ProjectCustomField;
import org.springframework.stereotype.Component;

/** Maps {@link ProjectCustomField} to its API representation. Plain, hand-written mapping. */
@Component
public class CustomFieldMapper {

    public CustomFieldResponse toResponse(ProjectCustomField customField) {
        return new CustomFieldResponse(
                customField.getId(),
                customField.getProjectId(),
                customField.getName(),
                customField.getValueType(),
                customField.getValue(),
                customField.isArchived(),
                customField.getCreatedAt(),
                customField.getUpdatedAt(),
                customField.getVersion());
    }
}
