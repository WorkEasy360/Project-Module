package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.projectmodule.work.domain.AIRecommendation;
import com.projectmodule.work.domain.RecommendationType;
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
class AIRecommendationEventRecorderTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectActivityRepository projectActivityRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    private AIRecommendationEventRecorder recorder;
    private RequestContext context;
    private AIRecommendation recommendation;

    @BeforeEach
    void setUp() {
        recorder = new AIRecommendationEventRecorder(projectActivityRepository, outboxMessageRepository,
                new ObjectMapper());
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        recommendation = AIRecommendation.create(UUID.randomUUID(), RecommendationType.RISK_ANALYSIS,
                null, null, "title", "rationale", null, USER);
    }

    @Test
    @DisplayName("recordCreated writes one ProjectActivity and one OutboxMessage with ai.recommendation.created")
    void recordCreatedWritesActivityAndOutbox() {
        recorder.recordCreated(context, recommendation);

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.AI_RECOMMENDATION_CREATED);

        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("ai.recommendation.created");
    }

    @Test
    @DisplayName("recordAccepted writes a ProjectActivity but no OutboxMessage")
    void recordAcceptedWritesActivityOnly() {
        recorder.recordAccepted(context, recommendation);

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.AI_RECOMMENDATION_ACCEPTED);
        verify(outboxMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordRejected writes a ProjectActivity but no OutboxMessage")
    void recordRejectedWritesActivityOnly() {
        recorder.recordRejected(context, recommendation);

        ArgumentCaptor<ProjectActivity> activityCaptor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(ActivityType.AI_RECOMMENDATION_REJECTED);
        verify(outboxMessageRepository, never()).save(any());
    }
}
