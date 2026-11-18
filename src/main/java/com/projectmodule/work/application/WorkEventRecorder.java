package com.projectmodule.work.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.automation.application.AutomationDispatcher;
import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.domain.event.TaskUpdated;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turns a Task domain event into its durable records: one {@link ProjectActivity} audit entry and
 * one {@link OutboxMessage}, reusing the same audit trail and outbox {@code Project} events write
 * to. {@code ProjectActivity.projectId} is the task's project, so a project's audit trail shows
 * its tasks' activity too.
 *
 * <p>Called synchronously, inside the same {@code @Transactional} method that changed the
 * aggregate — the same reasoning as {@code ProjectEventRecorder}: an
 * {@code @TransactionalEventListener(AFTER_COMMIT)} would break the atomicity guarantee that
 * the state change, the audit entry and the outbox record commit together or not at all.
 */
@Service
public class WorkEventRecorder {

    private final ProjectActivityRepository projectActivityRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final AutomationDispatcher automationDispatcher;

    public WorkEventRecorder(ProjectActivityRepository projectActivityRepository,
                             OutboxMessageRepository outboxMessageRepository,
                             ObjectMapper objectMapper,
                             AutomationDispatcher automationDispatcher) {
        this.projectActivityRepository = projectActivityRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.automationDispatcher = automationDispatcher;
    }

    /** Persists the audit and outbox record for one Task event, in the caller's existing transaction. */
    public void record(RequestContext context, Task task, TaskDomainEvent event) {
        String eventType = switch (event) {
            case TaskCreated ignored -> "task.created";
            case TaskUpdated ignored -> "task.updated";
            case TaskAssigned ignored -> "task.assigned";
            case TaskCompleted ignored -> "task.completed";
            case TaskBlocked ignored -> "task.blocked";
        };
        ActivityType activityType = switch (event) {
            case TaskCreated ignored -> ActivityType.TASK_CREATED;
            case TaskUpdated ignored -> ActivityType.TASK_UPDATED;
            case TaskAssigned ignored -> ActivityType.TASK_ASSIGNED;
            case TaskCompleted ignored -> ActivityType.TASK_COMPLETED;
            case TaskBlocked ignored -> ActivityType.TASK_BLOCKED;
        };
        String summary = switch (event) {
            case TaskCreated ignored -> "Task \"" + task.getName() + "\" was created";
            case TaskUpdated ignored -> "Task \"" + task.getName() + "\" was updated";
            case TaskAssigned ignored -> "Task \"" + task.getName() + "\" was assigned";
            case TaskCompleted ignored -> "Task \"" + task.getName() + "\" was completed";
            case TaskBlocked ignored -> "Task \"" + task.getName() + "\" was flagged blocked";
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("projectId", task.getProjectId());
        data.put("name", task.getName());
        data.put("status", task.getStatus());
        task.getAssigneeId().ifPresent(id -> data.put("assigneeId", id.value()));
        String payload = toJson(data, task.getId());

        persist(context, task.getProjectId(), task.getId(), "Task", activityType, eventType, summary, payload);
    }

    private void persist(RequestContext context,
                         java.util.UUID projectId,
                         java.util.UUID aggregateId,
                         String aggregateType,
                         ActivityType activityType,
                         String eventType,
                         String summary,
                         String payload) {
        projectActivityRepository.save(
                ProjectActivity.record(projectId, activityType, context, summary, payload));

        outboxMessageRepository.save(OutboxMessage.pending(
                aggregateType,
                aggregateId,
                eventType,
                payload,
                context.actorType(),
                context.userId().orElse(null),
                context.correlationId()));

        automationDispatcher.dispatch(projectId, eventType);
    }

    private String toJson(Map<String, Object> data, java.util.UUID aggregateId) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event payload for " + aggregateId, e);
        }
    }
}
