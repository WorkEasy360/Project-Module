package com.projectmodule.automation.api;

import com.projectmodule.automation.api.dto.AutomationResponse;
import com.projectmodule.automation.api.dto.AutomationRunResponse;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import org.springframework.stereotype.Component;

/** Maps {@link ProjectAutomation}/{@link AutomationRun} to their API representations. Plain, hand-written mapping. */
@Component
public class ProjectAutomationMapper {

    public AutomationResponse toResponse(ProjectAutomation automation) {
        return new AutomationResponse(
                automation.getId(),
                automation.getProjectId(),
                automation.getName(),
                automation.getDescription().orElse(null),
                automation.getTriggerEvent(),
                automation.getActionType(),
                automation.getActionRecipientId().map(id -> id.value()).orElse(null),
                automation.getActionChannelReference().orElse(null),
                automation.getActionMessage(),
                automation.isEnabled(),
                automation.isArchived(),
                automation.getCreatedAt(),
                automation.getUpdatedAt(),
                automation.getVersion());
    }

    public AutomationRunResponse toResponse(AutomationRun run) {
        return new AutomationRunResponse(
                run.getId(),
                run.getAutomationId(),
                run.getProjectId(),
                run.getTriggerEvent(),
                run.getStatus(),
                run.getErrorMessage().orElse(null),
                run.getExecutedAt());
    }
}
