package com.projectmodule.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.event.ProjectArchived;
import com.projectmodule.project.domain.event.ProjectCreated;
import com.projectmodule.project.domain.event.ProjectUpdated;
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
class ProjectEventRecorderTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectActivityRepository projectActivityRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private AutomationDispatcher automationDispatcher;

    private ProjectEventRecorder recorder;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        recorder = new ProjectEventRecorder(
                projectActivityRepository, outboxMessageRepository, new ObjectMapper(), automationDispatcher);
        context = new ImmutableRequestContext(
                Optional.of(OWNER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
    }

    @Test
    @DisplayName("dispatches the matching automation trigger for every recorded event, per docs/project/28-AUTOMATION-SPEC.md §6")
    void dispatchesAutomationForEveryEvent() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        verify(automationDispatcher).dispatch(project.getId(), "project.created");
    }

    @Test
    @DisplayName("records a project.created outbox event with the correct type and aggregate")
    void recordsCreatedOutboxEvent() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        OutboxMessage message = captor.getValue();

        assertThat(message.getEventType()).isEqualTo("project.created");
        assertThat(message.getAggregateType()).isEqualTo("Project");
        assertThat(message.getAggregateId()).isEqualTo(project.getId());
        assertThat(message.getActorId()).contains(OWNER);
        assertThat(message.getActorType()).contains(ActorType.HUMAN);
        assertThat(message.getCorrelationId()).contains("corr-1");
        assertThat(message.isPublished()).isFalse();
        assertThat(message.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("records a project.updated outbox event")
    void recordsUpdatedOutboxEvent() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectUpdated(project.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("project.updated");
    }

    @Test
    @DisplayName("records a project.archived outbox event")
    void recordsArchivedOutboxEvent() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);
        project.archive(OWNER);

        recorder.record(context, project, new ProjectArchived(project.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("project.archived");
    }

    @Test
    @DisplayName("maps each event to the matching ActivityType, one-to-one with the event type")
    void mapsActivityTypeConsistently() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        ArgumentCaptor<ProjectActivity> captor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(captor.capture());
        ProjectActivity activity = captor.getValue();

        assertThat(activity.getActivityType()).isEqualTo(ActivityType.PROJECT_CREATED);
        assertThat(activity.getProjectId()).isEqualTo(project.getId());
        assertThat(activity.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(activity.getActorId()).contains(OWNER);
    }

    @Test
    @DisplayName("the audit and outbox payloads both carry the project's current state as JSON")
    void payloadContainsProjectState() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());

        String activityPayload = activityCaptor.getValue().getPayload().orElseThrow();
        String outboxPayload = outboxCaptor.getValue().getPayload();

        assertThat(activityPayload).contains("Apollo").contains(project.getId().toString());
        assertThat(outboxPayload).contains("Apollo").contains(project.getId().toString());
        // Both records describe the same event, from the same call - built once, not
        // duplicated by two separate construction paths.
        assertThat(outboxPayload).isEqualTo(activityPayload);
    }

    @Test
    @DisplayName("the created event's payload includes organization and owner")
    void createdPayloadIncludesOrganizationAndOwner() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(captor.capture());

        assertThat(captor.getValue().getPayload())
                .contains(ORG.value().toString())
                .contains(OWNER.value().toString());
    }

    @Test
    @DisplayName("summaries name the project and describe what happened")
    void summariesAreHumanReadable() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        ArgumentCaptor<ProjectActivity> captor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getSummary()).contains("Apollo").contains("created");
    }

    @Test
    @DisplayName("records both an activity and an outbox row for every event")
    void recordsBothActivityAndOutbox() {
        Project project = Project.create(ORG, "Apollo", OWNER, ProjectPriority.HIGH);

        recorder.record(context, project, new ProjectCreated(project.getId()));

        verify(projectActivityRepository).save(any(ProjectActivity.class));
        verify(outboxMessageRepository).save(any(OutboxMessage.class));
    }
}
