package com.projectmodule.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.AutomationRunStatus;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.automation.infrastructure.AutomationRunRepository;
import com.projectmodule.automation.infrastructure.ProjectAutomationRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.NotificationPort;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves, against a real PostgreSQL 17 instance and the real Spring wiring (not Mockito for
 * {@code AutomationDispatcher} itself), that an existing mutation triggers a matching automation
 * end to end, and that the triggering request still succeeds even when the automation's action
 * fails — per {@code docs/project/28-AUTOMATION-SPEC.md} §11/§6.
 *
 * <p>{@link NotificationPort} is the one bean overridden (with {@code @MockitoBean}, replacing
 * the real {@code LoggingNotificationAdapter} for this test only) so the action can be forced to
 * fail deterministically; everything else — {@code TaskApplicationService},
 * {@code WorkEventRecorder}, {@code AutomationDispatcher}, and all persistence — is the real,
 * fully wired application, exercised against the real database.
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, matching every other real-database
 * test in this module.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class AutomationEndToEndTest {

    @Autowired
    private ProjectApplicationService projectApplicationService;

    @Autowired
    private TaskApplicationService taskApplicationService;

    @Autowired
    private ProjectAutomationRepository projectAutomationRepository;

    @Autowired
    private AutomationRunRepository automationRunRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private NotificationPort notificationPort;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());
    private final RequestContext context = new ImmutableRequestContext(
            Optional.of(owner), Optional.of(organization), "corr-e2e", ActorType.HUMAN);

    @Test
    @DisplayName("marking a task overdue fires a matching NOTIFY automation; when the action "
            + "fails, the task update still commits and the failure is recorded, not thrown")
    void taskOverdueTriggersAutomationEvenWhenActionFails() {
        doThrow(new RuntimeException("simulated notification failure"))
                .when(notificationPort).notify(any(), any(), any(), any());

        Project project = projectApplicationService.createProject(
                context, new CreateProjectRequest("E2E Automation Project", null, ProjectPriority.MEDIUM, null, null));
        Task task = taskApplicationService.createTask(
                context, project.getId(), new CreateTaskRequest("Ship feature", null, null, null));

        ProjectAutomation automation = projectAutomationRepository.saveAndFlush(
                ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                        AutomationActionType.NOTIFY, UUID.randomUUID(), null, "A task is overdue"));

        assertThatCode(() -> taskApplicationService.updateTask(context, task.getId(),
                new UpdateTaskRequest(null, null, null, null, TaskStatus.OVERDUE, task.getVersion())))
                .as("the triggering request must succeed even though the automation's action fails")
                .doesNotThrowAnyException();

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("the task status change must have committed")
                .isEqualTo(TaskStatus.OVERDUE);

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByExecutedAtDesc(automation.getId());
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(runs.get(0).getErrorMessage()).contains("simulated notification failure");
    }

    @Test
    @DisplayName("a disabled automation does not fire, and no AutomationRun is recorded for it")
    void disabledAutomationDoesNotFire() {
        Project project = projectApplicationService.createProject(
                context, new CreateProjectRequest("E2E Disabled Automation Project", null, ProjectPriority.MEDIUM, null, null));
        Task task = taskApplicationService.createTask(
                context, project.getId(), new CreateTaskRequest("Ship feature", null, null, null));

        ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify on overdue (disabled)",
                "task.overdue", AutomationActionType.NOTIFY, UUID.randomUUID(), null, "A task is overdue");
        automation.disable();
        projectAutomationRepository.saveAndFlush(automation);

        taskApplicationService.updateTask(context, task.getId(),
                new UpdateTaskRequest(null, null, null, null, TaskStatus.OVERDUE, task.getVersion()));

        assertThat(automationRunRepository.findByAutomationIdOrderByExecutedAtDesc(automation.getId())).isEmpty();
    }
}
