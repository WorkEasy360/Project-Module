package com.projectmodule.work.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.work.domain.AIRecommendation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turns an {@link AIRecommendation} lifecycle change into its durable records, per
 * {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md} §6 — the same
 * {@link ProjectActivity}+{@link OutboxMessage} shape {@link WorkEventRecorder} already
 * establishes, called synchronously in the caller's existing transaction.
 *
 * <p>{@code 05-EVENTS.md} catalogues exactly one AI recommendation event —
 * {@code ai.recommendation.created} — so only creation raises an outbox message. Accept/reject
 * still write a {@link ProjectActivity} audit entry (an internal enum, not part of the event
 * catalogue) but raise no outbox event, since none is catalogued for them.
 */
@Service
public class AIRecommendationEventRecorder {

    private static final String AGGREGATE_TYPE = "AIRecommendation";

    private final ProjectActivityRepository projectActivityRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public AIRecommendationEventRecorder(ProjectActivityRepository projectActivityRepository,
                                         OutboxMessageRepository outboxMessageRepository,
                                         ObjectMapper objectMapper) {
        this.projectActivityRepository = projectActivityRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /** Persists the audit and outbox record for a newly created recommendation. */
    public void recordCreated(RequestContext context, AIRecommendation recommendation) {
        String summary = "An AI recommendation (" + recommendation.getType() + ") was created";
        String payload = payloadFor(recommendation);

        projectActivityRepository.save(ProjectActivity.record(
                recommendation.getProjectId(), ActivityType.AI_RECOMMENDATION_CREATED, context, summary, payload));

        outboxMessageRepository.save(OutboxMessage.pending(
                AGGREGATE_TYPE,
                recommendation.getId(),
                "ai.recommendation.created",
                payload,
                context.actorType(),
                context.userId().orElse(null),
                context.correlationId()));
    }

    /** Persists the audit entry for an accepted recommendation. No outbox event — none is catalogued. */
    public void recordAccepted(RequestContext context, AIRecommendation recommendation) {
        recordResponse(context, recommendation, ActivityType.AI_RECOMMENDATION_ACCEPTED, "accepted");
    }

    /** Persists the audit entry for a rejected recommendation. No outbox event — none is catalogued. */
    public void recordRejected(RequestContext context, AIRecommendation recommendation) {
        recordResponse(context, recommendation, ActivityType.AI_RECOMMENDATION_REJECTED, "rejected");
    }

    private void recordResponse(RequestContext context, AIRecommendation recommendation,
                                ActivityType activityType, String verb) {
        String summary = "An AI recommendation (" + recommendation.getType() + ") was " + verb;
        projectActivityRepository.save(ProjectActivity.record(
                recommendation.getProjectId(), activityType, context, summary, payloadFor(recommendation)));
    }

    private String payloadFor(AIRecommendation recommendation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendationId", recommendation.getId());
        data.put("projectId", recommendation.getProjectId());
        data.put("type", recommendation.getType());
        data.put("status", recommendation.getStatus());
        recommendation.getResourceId().ifPresent(id -> data.put("resourceId", id));

        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event payload for AI recommendation " + recommendation.getId(), e);
        }
    }
}
