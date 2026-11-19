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
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
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
    @DisplayName("dispatches the matching automation trigger for every recorded event, per docs/project/28-AUTOMATION-SPEC.md §6")
    void dispatchesAutomationForEveryEvent() {
        Task task = Task.create(PROJECT_ID, "Write spec", null, null);

        recorder.record(context, task, new TaskCreated(task.getId()));

        verify(automationDispatcher).dispatch(PROJECT_ID, "task.created");
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

}
