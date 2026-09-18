package com.projectmodule.automation.application;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.automation.infrastructure.AutomationRunRepository;
import com.projectmodule.automation.infrastructure.ProjectAutomationRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuration (not execution — see {@link AutomationDispatcher}) for automation rules, per the
 * approved {@code docs/project/28-AUTOMATION-SPEC.md} §8/§9. Same authorization pattern as every
 * other project-scoped service.
 */
@Service
public class ProjectAutomationApplicationService {

    private final ProjectAutomationRepository projectAutomationRepository;
    private final AutomationRunRepository automationRunRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectAutomationApplicationService(ProjectAutomationRepository projectAutomationRepository,
                                               AutomationRunRepository automationRunRepository,
                                               ProjectApplicationService projectApplicationService,
                                               ProjectAuthorizationService projectAuthorizationService) {
        this.projectAutomationRepository = projectAutomationRepository;
        this.automationRunRepository = automationRunRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public ProjectAutomation createAutomation(RequestContext context, UUID projectId, String name,
                                              String description, String triggerEvent,
                                              AutomationActionType actionType,
                                              UUID actionRecipientId, String actionChannelReference,
                                              String actionMessage) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        ProjectAutomation automation = ProjectAutomation.create(projectId, name, triggerEvent, actionType,
                actionRecipientId, actionChannelReference, actionMessage);
        if (description != null) {
            automation.describe(description);
        }
        return projectAutomationRepository.save(automation);
    }

    @Transactional(readOnly = true)
    public List<ProjectAutomation> listAutomations(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return projectAutomationRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public ProjectAutomation updateAutomation(RequestContext context, UUID automationId, String name,
                                              String description, String actionMessage, Boolean enabled,
                                              long expectedVersion) {
        ProjectAutomation automation = findActive(automationId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, automation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), automation.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (automation.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "ProjectAutomation " + automationId + " has changed since version " + expectedVersion
                            + "; reload and try again");
        }

        if (name != null) {
            automation.rename(name);
        }
        if (description != null) {
            automation.describe(description);
        }
        if (actionMessage != null) {
            automation.updateMessage(actionMessage);
        }
        if (enabled != null) {
            if (enabled) {
                automation.enable();
            } else {
                automation.disable();
            }
        }

        return projectAutomationRepository.save(automation);
    }

    @Transactional
    public void archiveAutomation(RequestContext context, UUID automationId) {
        ProjectAutomation automation = findActive(automationId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, automation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), automation.getProjectId(), ProjectPermission.EDIT_PROJECT);

        automation.archive(context.requireUserId());
        projectAutomationRepository.save(automation);
    }

    /** Run history remains visible after the automation itself is archived, the same convention {@code ProjectActivity} already establishes for archived work items. */
    @Transactional(readOnly = true)
    public List<AutomationRun> listRuns(RequestContext context, UUID automationId) {
        ProjectAutomation automation = projectAutomationRepository.findById(automationId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectAutomation", automationId));
        projectApplicationService.findActiveProjectInCallerOrganization(context, automation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), automation.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return automationRunRepository.findByAutomationIdOrderByExecutedAtDesc(automationId);
    }

    private ProjectAutomation findActive(UUID automationId) {
        return projectAutomationRepository.findByIdAndArchivedAtIsNull(automationId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectAutomation", automationId));
    }
}
