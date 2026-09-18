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
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.DependencyBroken;
import com.projectmodule.work.domain.event.DependencyCreated;
import com.projectmodule.work.domain.event.DependencyDomainEvent;
import com.projectmodule.work.domain.event.MilestoneAtRisk;
import com.projectmodule.work.domain.event.MilestoneCompleted;
import com.projectmodule.work.domain.event.MilestoneCreated;
import com.projectmodule.work.domain.event.MilestoneDomainEvent;
import com.projectmodule.work.domain.event.PhaseCreated;
import com.projectmodule.work.domain.event.PhaseDomainEvent;
import com.projectmodule.work.domain.event.PhaseUpdated;
import com.projectmodule.work.domain.event.RiskCreated;
import com.projectmodule.work.domain.event.RiskDomainEvent;
import com.projectmodule.work.domain.event.RiskResolved;
import com.projectmodule.work.domain.event.RiskUpdated;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.domain.event.TaskOverdue;
import com.projectmodule.work.domain.event.TaskUpdated;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Turns a Phase, Milestone, Task, Dependency or Risk domain event into its durable records: one
 * {@link ProjectActivity} audit entry and one {@link OutboxMessage} — reusing the exact same
 * audit trail and outbox {@code Project} events already write to, rather than building parallel
 * infrastructure for work items. {@code ProjectActivity.projectId} is the aggregate's owning
 * project, so a project's audit trail shows its phases', milestones', tasks' and dependencies'
 * activity too.
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

    /** Persists the audit and outbox record for one Phase event, in the caller's existing transaction. */
    public void record(RequestContext context, Phase phase, PhaseDomainEvent event) {
        String eventType = switch (event) {
            case PhaseCreated ignored -> "phase.created";
            case PhaseUpdated ignored -> "phase.updated";
        };
        ActivityType activityType = switch (event) {
            case PhaseCreated ignored -> ActivityType.PHASE_CREATED;
            case PhaseUpdated ignored -> ActivityType.PHASE_UPDATED;
        };
        String summary = switch (event) {
            case PhaseCreated ignored -> "Phase \"" + phase.getName() + "\" was created";
            case PhaseUpdated ignored -> "Phase \"" + phase.getName() + "\" was updated";
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phaseId", phase.getId());
        data.put("projectId", phase.getProjectId());
        data.put("name", phase.getName());
        String payload = toJson(data, phase.getId());

        persist(context, phase.getProjectId(), phase.getId(), "Phase", activityType, eventType, summary, payload);
    }

    /** Persists the audit and outbox record for one Milestone event, in the caller's existing transaction. */
    public void record(RequestContext context, Milestone milestone, MilestoneDomainEvent event) {
        String eventType = switch (event) {
            case MilestoneCreated ignored -> "milestone.created";
            case MilestoneCompleted ignored -> "milestone.completed";
            case MilestoneAtRisk ignored -> "milestone.at_risk";
        };
        ActivityType activityType = switch (event) {
            case MilestoneCreated ignored -> ActivityType.MILESTONE_CREATED;
            case MilestoneCompleted ignored -> ActivityType.MILESTONE_COMPLETED;
            case MilestoneAtRisk ignored -> ActivityType.MILESTONE_AT_RISK;
        };
        String summary = switch (event) {
            case MilestoneCreated ignored -> "Milestone \"" + milestone.getName() + "\" was created";
            case MilestoneCompleted ignored -> "Milestone \"" + milestone.getName() + "\" was completed";
            case MilestoneAtRisk ignored -> "Milestone \"" + milestone.getName() + "\" was flagged at risk";
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("milestoneId", milestone.getId());
        data.put("projectId", milestone.getProjectId());
        data.put("name", milestone.getName());
        data.put("status", milestone.getStatus());
        String payload = toJson(data, milestone.getId());

        persist(context, milestone.getProjectId(), milestone.getId(), "Milestone",
                activityType, eventType, summary, payload);
    }

    /** Persists the audit and outbox record for one Task event, in the caller's existing transaction. */
    public void record(RequestContext context, Task task, TaskDomainEvent event) {
        String eventType = switch (event) {
            case TaskCreated ignored -> "task.created";
            case TaskUpdated ignored -> "task.updated";
            case TaskAssigned ignored -> "task.assigned";
            case TaskCompleted ignored -> "task.completed";
            case TaskBlocked ignored -> "task.blocked";
            case TaskOverdue ignored -> "task.overdue";
        };
        ActivityType activityType = switch (event) {
            case TaskCreated ignored -> ActivityType.TASK_CREATED;
            case TaskUpdated ignored -> ActivityType.TASK_UPDATED;
            case TaskAssigned ignored -> ActivityType.TASK_ASSIGNED;
            case TaskCompleted ignored -> ActivityType.TASK_COMPLETED;
            case TaskBlocked ignored -> ActivityType.TASK_BLOCKED;
            case TaskOverdue ignored -> ActivityType.TASK_OVERDUE;
        };
        String summary = switch (event) {
            case TaskCreated ignored -> "Task \"" + task.getName() + "\" was created";
            case TaskUpdated ignored -> "Task \"" + task.getName() + "\" was updated";
            case TaskAssigned ignored -> "Task \"" + task.getName() + "\" was assigned";
            case TaskCompleted ignored -> "Task \"" + task.getName() + "\" was completed";
            case TaskBlocked ignored -> "Task \"" + task.getName() + "\" was flagged blocked";
            case TaskOverdue ignored -> "Task \"" + task.getName() + "\" was flagged overdue";
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

    /**
     * Persists the audit and outbox record for one Dependency event, in the caller's existing
     * transaction.
     *
     * @param projectId the owning project, resolved by the caller from the dependent task —
     *                   unlike Phase/Milestone/Task, {@code Dependency} stores no
     *                   {@code projectId} of its own, only the two task ids
     */
    public void record(RequestContext context, Dependency dependency, UUID projectId, DependencyDomainEvent event) {
        String eventType = switch (event) {
            case DependencyCreated ignored -> "dependency.created";
            case DependencyBroken ignored -> "dependency.broken";
        };
        ActivityType activityType = switch (event) {
            case DependencyCreated ignored -> ActivityType.DEPENDENCY_CREATED;
            case DependencyBroken ignored -> ActivityType.DEPENDENCY_BROKEN;
        };
        String summary = switch (event) {
            case DependencyCreated ignored -> "A dependency was created";
            case DependencyBroken ignored -> "A dependency was flagged broken";
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dependencyId", dependency.getId());
        data.put("dependentTaskId", dependency.getDependentTaskId());
        data.put("prerequisiteTaskId", dependency.getPrerequisiteTaskId());
        data.put("broken", dependency.isBroken());
        String payload = toJson(data, dependency.getId());

        persist(context, projectId, dependency.getId(), "Dependency", activityType, eventType, summary, payload);
    }

    /** Persists the audit and outbox record for one Risk event, in the caller's existing transaction. */
    public void record(RequestContext context, Risk risk, RiskDomainEvent event) {
        String eventType = switch (event) {
            case RiskCreated ignored -> "risk.created";
            case RiskUpdated ignored -> "risk.updated";
            case RiskResolved ignored -> "risk.resolved";
        };
        ActivityType activityType = switch (event) {
            case RiskCreated ignored -> ActivityType.RISK_CREATED;
            case RiskUpdated ignored -> ActivityType.RISK_UPDATED;
            case RiskResolved ignored -> ActivityType.RISK_RESOLVED;
        };
        String summary = switch (event) {
            case RiskCreated ignored -> "Risk \"" + risk.getName() + "\" was created";
            case RiskUpdated ignored -> "Risk \"" + risk.getName() + "\" was updated";
            case RiskResolved ignored -> "Risk \"" + risk.getName() + "\" was resolved";
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("riskId", risk.getId());
        data.put("projectId", risk.getProjectId());
        data.put("name", risk.getName());
        data.put("priority", risk.getPriority());
        data.put("status", risk.getStatus());
        String payload = toJson(data, risk.getId());

        persist(context, risk.getProjectId(), risk.getId(), "Risk", activityType, eventType, summary, payload);
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
