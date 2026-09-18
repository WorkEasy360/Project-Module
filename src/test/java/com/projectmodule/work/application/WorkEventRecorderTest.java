package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.automation.application.AutomationDispatcher;
import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.DependencyBroken;
import com.projectmodule.work.domain.event.DependencyCreated;
import com.projectmodule.work.domain.event.MilestoneAtRisk;
import com.projectmodule.work.domain.event.MilestoneCompleted;
import com.projectmodule.work.domain.event.MilestoneCreated;
import com.projectmodule.work.domain.event.PhaseCreated;
import com.projectmodule.work.domain.event.PhaseUpdated;
import com.projectmodule.work.domain.event.RiskCreated;
import com.projectmodule.work.domain.event.RiskResolved;
import com.projectmodule.work.domain.event.RiskUpdated;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
import com.projectmodule.work.domain.event.TaskOverdue;
import java.time.LocalDate;
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
class WorkEventRecorderTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectActivityRepository projectActivityRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private AutomationDispatcher automationDispatcher;

    private WorkEventRecorder recorder;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        recorder = new WorkEventRecorder(
                projectActivityRepository, outboxMessageRepository, new ObjectMapper(), automationDispatcher);
        context = new ImmutableRequestContext(
                Optional.of(OWNER), Optional.empty(), "corr-1", ActorType.HUMAN);
    }

    @Test
    @DisplayName("records a phase.created outbox event against the Phase aggregate")
    void recordsPhaseCreated() {
        Phase phase = Phase.create(PROJECT_ID, "Discovery");

        recorder.record(context, phase, new PhaseCreated(phase.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("phase.created");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Phase");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(phase.getId());

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.PHASE_CREATED);
        assertThat(activityCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("dispatches the matching automation trigger for every recorded event, per docs/project/28-AUTOMATION-SPEC.md §6")
    void dispatchesAutomationForEveryEvent() {
        Phase phase = Phase.create(PROJECT_ID, "Discovery");

        recorder.record(context, phase, new PhaseCreated(phase.getId()));

        verify(automationDispatcher).dispatch(PROJECT_ID, "phase.created");
    }

    @Test
    @DisplayName("records a phase.updated outbox event")
    void recordsPhaseUpdated() {
        Phase phase = Phase.create(PROJECT_ID, "Discovery");

        recorder.record(context, phase, new PhaseUpdated(phase.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("phase.updated");
    }

    @Test
    @DisplayName("records a milestone.created outbox event against the Milestone aggregate")
    void recordsMilestoneCreated() {
        Milestone milestone = Milestone.create(PROJECT_ID, null, "Beta", LocalDate.of(2026, 3, 1));

        recorder.record(context, milestone, new MilestoneCreated(milestone.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("milestone.created");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Milestone");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(milestone.getId());

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.MILESTONE_CREATED);
    }

    @Test
    @DisplayName("records a milestone.completed outbox event")
    void recordsMilestoneCompleted() {
        Milestone milestone = Milestone.create(PROJECT_ID, null, "Beta", null);
        milestone.complete();

        recorder.record(context, milestone, new MilestoneCompleted(milestone.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("milestone.completed");
    }

    @Test
    @DisplayName("records a milestone.at_risk outbox event")
    void recordsMilestoneAtRisk() {
        Milestone milestone = Milestone.create(PROJECT_ID, null, "Beta", null);
        milestone.markAtRisk();

        recorder.record(context, milestone, new MilestoneAtRisk(milestone.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("milestone.at_risk");
    }

    @Test
    @DisplayName("records a task.created outbox event against the Task aggregate")
    void recordsTaskCreated() {
        Task task = Task.create(PROJECT_ID, "Implement login", null, null);

        recorder.record(context, task, new TaskCreated(task.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("task.created");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Task");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(task.getId());

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.TASK_CREATED);
    }

    @Test
    @DisplayName("records a task.assigned outbox event")
    void recordsTaskAssigned() {
        Task task = Task.create(PROJECT_ID, "Implement login", null, null);
        task.assign(OWNER);

        recorder.record(context, task, new TaskAssigned(task.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("task.assigned");
        assertThat(captor.getValue().getPayload()).contains(OWNER.value().toString());
    }

    @Test
    @DisplayName("records a task.completed outbox event")
    void recordsTaskCompleted() {
        Task task = Task.create(PROJECT_ID, "Implement login", null, null);
        task.complete();

        recorder.record(context, task, new TaskCompleted(task.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("task.completed");
    }

    @Test
    @DisplayName("records a task.blocked outbox event")
    void recordsTaskBlocked() {
        Task task = Task.create(PROJECT_ID, "Implement login", null, null);
        task.markBlocked();

        recorder.record(context, task, new TaskBlocked(task.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("task.blocked");
    }

    @Test
    @DisplayName("records a task.overdue outbox event")
    void recordsTaskOverdue() {
        Task task = Task.create(PROJECT_ID, "Implement login", null, null);
        task.markOverdue();

        recorder.record(context, task, new TaskOverdue(task.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("task.overdue");
    }

    @Test
    @DisplayName("records a dependency.created outbox event against the Dependency aggregate")
    void recordsDependencyCreated() {
        Dependency dependency = Dependency.create(UUID.randomUUID(), UUID.randomUUID());

        recorder.record(context, dependency, PROJECT_ID, new DependencyCreated(dependency.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("dependency.created");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Dependency");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(dependency.getId());

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.DEPENDENCY_CREATED);
        assertThat(activityCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("records a dependency.broken outbox event")
    void recordsDependencyBroken() {
        Dependency dependency = Dependency.create(UUID.randomUUID(), UUID.randomUUID());
        dependency.markBroken();

        recorder.record(context, dependency, PROJECT_ID, new DependencyBroken(dependency.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("dependency.broken");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Dependency");
    }

    @Test
    @DisplayName("records a risk.created outbox event against the Risk aggregate")
    void recordsRiskCreated() {
        Risk risk = Risk.create(PROJECT_ID, "Vendor delay", ProjectPriority.HIGH);

        recorder.record(context, risk, new RiskCreated(risk.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("risk.created");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Risk");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(risk.getId());

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.RISK_CREATED);
        assertThat(activityCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("records a risk.updated outbox event")
    void recordsRiskUpdated() {
        Risk risk = Risk.create(PROJECT_ID, "Vendor delay", ProjectPriority.HIGH);

        recorder.record(context, risk, new RiskUpdated(risk.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("risk.updated");
    }

    @Test
    @DisplayName("records a risk.resolved outbox event")
    void recordsRiskResolved() {
        Risk risk = Risk.create(PROJECT_ID, "Vendor delay", ProjectPriority.HIGH);
        risk.resolve();

        recorder.record(context, risk, new RiskResolved(risk.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("risk.resolved");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("Risk");
    }
}
