package com.projectmodule.project.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.automation.application.AutomationDispatcher;
import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.event.ProjectArchived;
import com.projectmodule.project.domain.event.ProjectCreated;
import com.projectmodule.project.domain.event.ProjectDomainEvent;
import com.projectmodule.project.domain.event.ProjectUpdated;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turns a {@link ProjectDomainEvent} into its durable records: one {@link ProjectActivity}
 * audit entry and one {@link OutboxMessage}.
 *
 * <p>Called synchronously from {@link ProjectApplicationService}, inside the same
 * {@code @Transactional} method that changed the project — not through Spring's
 * {@code ApplicationEventPublisher}. An {@code @TransactionalEventListener(AFTER_COMMIT)}
 * listener would run in a separate transaction after the state change had already committed,
 * which would break the guarantee this slice requires: the state change, the audit entry and
 * the outbox record either all commit together or none of them do. Nothing here delivers
 * anything outside the database — {@code OutboxMessage.publishedAt} is left {@code null}, its
 * correct initial state; a publisher is a later slice.
 */
@Service
public class ProjectEventRecorder {

    private static final String AGGREGATE_TYPE = "Project";

    private final ProjectActivityRepository projectActivityRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final AutomationDispatcher automationDispatcher;

    public ProjectEventRecorder(ProjectActivityRepository projectActivityRepository,
                                OutboxMessageRepository outboxMessageRepository,
                                ObjectMapper objectMapper,
                                AutomationDispatcher automationDispatcher) {
        this.projectActivityRepository = projectActivityRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.automationDispatcher = automationDispatcher;
    }

    /**
     * Persists the audit and outbox record for one event, in the caller's existing transaction.
     *
     * @param context the caller whose action raised the event; supplies actor identity, actor
     *                type and correlation id for both records
     * @param project the aggregate as it stands after the domain change, used to build the
     *                summary and payload
     */
    public void record(RequestContext context, Project project, ProjectDomainEvent event) {
        String eventType = eventTypeOf(event);
        ActivityType activityType = activityTypeOf(event);
        String summary = summaryFor(event, project);
        String payload = payloadFor(event, project);

        projectActivityRepository.save(
                ProjectActivity.record(project.getId(), activityType, context, summary, payload));

        outboxMessageRepository.save(OutboxMessage.pending(
                AGGREGATE_TYPE,
                project.getId(),
                eventType,
                payload,
                context.actorType(),
                context.userId().orElse(null),
                context.correlationId()));

        automationDispatcher.dispatch(project.getId(), eventType);
    }

    private static String eventTypeOf(ProjectDomainEvent event) {
        return switch (event) {
            case ProjectCreated ignored -> "project.created";
            case ProjectUpdated ignored -> "project.updated";
            case ProjectArchived ignored -> "project.archived";
        };
    }

    private static ActivityType activityTypeOf(ProjectDomainEvent event) {
        return switch (event) {
            case ProjectCreated ignored -> ActivityType.PROJECT_CREATED;
            case ProjectUpdated ignored -> ActivityType.PROJECT_UPDATED;
            case ProjectArchived ignored -> ActivityType.PROJECT_ARCHIVED;
        };
    }

    private static String summaryFor(ProjectDomainEvent event, Project project) {
        return switch (event) {
            case ProjectCreated ignored -> "Project \"" + project.getName() + "\" was created";
            case ProjectUpdated ignored -> "Project \"" + project.getName() + "\" was updated";
            case ProjectArchived ignored -> "Project \"" + project.getName() + "\" was archived";
        };
    }

    /**
     * A small snapshot of the project's current state, shared by the activity and outbox
     * records for one event — built once, not duplicated between the two call sites.
     */
    private String payloadFor(ProjectDomainEvent event, Project project) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", project.getId());
        data.put("name", project.getName());
        data.put("status", project.getStatus());
        data.put("priority", project.getPriority());

        if (event instanceof ProjectCreated) {
            data.put("organizationId", project.getOrganizationId().value());
            data.put("ownerId", project.getOwnerId().value());
        }
        if (event instanceof ProjectArchived) {
            project.getArchivedBy().ifPresent(id -> data.put("archivedBy", id.value()));
        }

        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // The payload is a small map of UUIDs, strings and enums - not expected to fail.
            throw new IllegalStateException("Failed to serialize event payload for project "
                    + project.getId(), e);
        }
    }
}
