package com.projectmodule.automation.application;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.automation.infrastructure.AutomationRunRepository;
import com.projectmodule.automation.infrastructure.ProjectAutomationRepository;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.integration.port.ChatPort;
import com.projectmodule.integration.port.NotificationPort;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matches enabled, non-archived {@link ProjectAutomation} rules to a fired event and executes
 * their action, per the approved {@code docs/project/28-AUTOMATION-SPEC.md} §6.
 *
 * <p>Called synchronously from {@code WorkEventRecorder}/{@code ProjectEventRecorder}, in the
 * same transaction as the mutation that raised the event — the minimal-diff hook point, since
 * every domain event already funnels through those two classes. An action failure is caught and
 * recorded as a {@link AutomationRun#failed}, never propagated: the triggering mutation's own
 * transaction must not fail because a notification/chat delivery had a problem.
 *
 * <p>Not gated by {@code ProjectAuthorizationService} — the same precedent
 * {@code DashboardApplicationService} already establishes for a system-internal operation with
 * no single human caller to check a permission against. Authorization applies at
 * *configuration* time ({@link ProjectAutomationApplicationService}), not at execution time.
 *
 * <p>Reads the project via {@link ProjectRepository} directly, not
 * {@code ProjectApplicationService} — the mutation that raised the triggering event already
 * resolved and authorized that project before this dispatcher was ever reached (the same
 * organization-boundary check has already happened once, upstream), and
 * {@code ProjectApplicationService} itself depends on {@code ProjectEventRecorder}, which now
 * depends on this class; going through the application service here would be a circular bean
 * dependency, not just a redundant check.
 */
@Service
public class AutomationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final ProjectAutomationRepository projectAutomationRepository;
    private final AutomationRunRepository automationRunRepository;
    private final NotificationPort notificationPort;
    private final ChatPort chatPort;
    private final ProjectRepository projectRepository;

    public AutomationDispatcher(ProjectAutomationRepository projectAutomationRepository,
                                AutomationRunRepository automationRunRepository,
                                NotificationPort notificationPort,
                                ChatPort chatPort,
                                ProjectRepository projectRepository) {
        this.projectAutomationRepository = projectAutomationRepository;
        this.automationRunRepository = automationRunRepository;
        this.notificationPort = notificationPort;
        this.chatPort = chatPort;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void dispatch(UUID projectId, String eventType) {
        List<ProjectAutomation> candidates = projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(projectId, eventType);
        if (candidates.isEmpty()) {
            return;
        }

        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        for (ProjectAutomation automation : candidates) {
            executeOne(project, automation, eventType);
        }
    }

    private void executeOne(Project project, ProjectAutomation automation, String eventType) {
        try {
            executeAction(project, automation);
            automationRunRepository.save(
                    AutomationRun.succeeded(automation.getId(), project.getId(), eventType));
        } catch (RuntimeException e) {
            log.warn("Automation {} failed for project {} on event {}: {}",
                    automation.getId(), project.getId(), eventType, e.getMessage());
            automationRunRepository.save(
                    AutomationRun.failed(automation.getId(), project.getId(), eventType, e.getMessage()));
        }
    }

    private void executeAction(Project project, ProjectAutomation automation) {
        if (automation.getActionType() == AutomationActionType.NOTIFY) {
            notificationPort.notify(
                    project.getOrganizationId(),
                    automation.getActionRecipientId().orElseThrow(),
                    automation.getName(),
                    automation.getActionMessage());
        } else if (automation.getActionType() == AutomationActionType.CHAT_MESSAGE) {
            chatPort.postMessage(
                    project.getOrganizationId(),
                    automation.getActionChannelReference().orElseThrow(),
                    automation.getActionMessage());
        }
    }
}
