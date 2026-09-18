package com.projectmodule.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.AutomationRunStatus;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.automation.infrastructure.AutomationRunRepository;
import com.projectmodule.automation.infrastructure.ProjectAutomationRepository;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.ChatPort;
import com.projectmodule.integration.port.NotificationPort;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomationDispatcherTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectAutomationRepository projectAutomationRepository;

    @Mock
    private AutomationRunRepository automationRunRepository;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private ChatPort chatPort;

    @Mock
    private ProjectRepository projectRepository;

    private AutomationDispatcher dispatcher;
    private Project project;

    @BeforeEach
    void setUp() {
        dispatcher = new AutomationDispatcher(
                projectAutomationRepository, automationRunRepository, notificationPort, chatPort, projectRepository);
        project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.MEDIUM);
    }

    @Test
    @DisplayName("does nothing when no automation matches the trigger")
    void doesNothingWhenNoMatch() {
        when(projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(project.getId(), "task.overdue"))
                .thenReturn(List.of());

        dispatcher.dispatch(project.getId(), "task.overdue");

        verify(projectRepository, never()).findByIdAndArchivedAtIsNull(any());
        verify(automationRunRepository, never()).save(any());
    }

    @Test
    @DisplayName("NOTIFY calls NotificationPort with the rule's recipient and message, records SUCCEEDED")
    void notifyCallsNotificationPort() {
        UUID recipientId = UUID.randomUUID();
        ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify on overdue",
                "task.overdue", AutomationActionType.NOTIFY, recipientId, null, "A task is overdue");
        when(projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(project.getId(), "task.overdue"))
                .thenReturn(List.of(automation));
        when(projectRepository.findByIdAndArchivedAtIsNull(project.getId())).thenReturn(Optional.of(project));

        dispatcher.dispatch(project.getId(), "task.overdue");

        verify(notificationPort).notify(ORG, ExternalUserId.of(recipientId), "Notify on overdue", "A task is overdue");

        ArgumentCaptor<AutomationRun> runCaptor = ArgumentCaptor.forClass(AutomationRun.class);
        verify(automationRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(AutomationRunStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("CHAT_MESSAGE calls ChatPort with the rule's channel and message")
    void chatMessageCallsChatPort() {
        ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify chat",
                "risk.created", AutomationActionType.CHAT_MESSAGE, null, "#risks", "A new risk was created");
        when(projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(project.getId(), "risk.created"))
                .thenReturn(List.of(automation));
        when(projectRepository.findByIdAndArchivedAtIsNull(project.getId())).thenReturn(Optional.of(project));

        dispatcher.dispatch(project.getId(), "risk.created");

        verify(chatPort).postMessage(ORG, "#risks", "A new risk was created");
    }

    @Test
    @DisplayName("an action failure is caught, recorded as FAILED, and does not propagate")
    void actionFailureIsCaughtAndRecorded() {
        UUID recipientId = UUID.randomUUID();
        ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify on overdue",
                "task.overdue", AutomationActionType.NOTIFY, recipientId, null, "A task is overdue");
        when(projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(project.getId(), "task.overdue"))
                .thenReturn(List.of(automation));
        when(projectRepository.findByIdAndArchivedAtIsNull(project.getId())).thenReturn(Optional.of(project));
        doThrow(new RuntimeException("Notification port unavailable"))
                .when(notificationPort).notify(any(), any(), any(), any());

        assertThatCode(() -> dispatcher.dispatch(project.getId(), "task.overdue")).doesNotThrowAnyException();

        ArgumentCaptor<AutomationRun> runCaptor = ArgumentCaptor.forClass(AutomationRun.class);
        verify(automationRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(runCaptor.getValue().getErrorMessage()).contains("Notification port unavailable");
    }

    @Test
    @DisplayName("multiple matching rules each execute and each get their own run record")
    void multipleMatchingRulesEachExecute() {
        ProjectAutomation first = ProjectAutomation.create(project.getId(), "First", "task.overdue",
                AutomationActionType.NOTIFY, UUID.randomUUID(), null, "m1");
        ProjectAutomation second = ProjectAutomation.create(project.getId(), "Second", "task.overdue",
                AutomationActionType.CHAT_MESSAGE, null, "#ch", "m2");
        when(projectAutomationRepository
                .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(project.getId(), "task.overdue"))
                .thenReturn(List.of(first, second));
        when(projectRepository.findByIdAndArchivedAtIsNull(project.getId())).thenReturn(Optional.of(project));

        dispatcher.dispatch(project.getId(), "task.overdue");

        verify(notificationPort).notify(any(), any(), any(), any());
        verify(chatPort).postMessage(any(), any(), any());
        verify(automationRunRepository, times(2)).save(any());
    }
}
