package com.projectmodule.collaboration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectActivityTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    @Test
    @DisplayName("records an identified human action")
    void recordsHumanAction() {
        ProjectActivity activity = ProjectActivity.record(PROJECT_ID, ActivityType.PROJECT_CREATED,
                ActorType.HUMAN, ACTOR, "Created project Apollo", null, "corr-1");

        assertThat(activity.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(activity.getActorId()).contains(ACTOR);
        assertThat(activity.getCorrelationId()).contains("corr-1");
        assertThat(activity.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("takes actor and correlation id from the caller context")
    void recordsFromRequestContext() {
        RequestContext context = new ImmutableRequestContext(
                Optional.of(ACTOR), Optional.empty(), "corr-9", ActorType.HUMAN);

        ProjectActivity activity = ProjectActivity.record(
                PROJECT_ID, ActivityType.PROJECT_UPDATED, context, "Renamed project", null);

        assertThat(activity.getActorId()).contains(ACTOR);
        assertThat(activity.getCorrelationId()).contains("corr-9");
    }

    @Test
    @DisplayName("keeps an AI action distinguishable from a human one")
    void distinguishesAiActor() {
        ProjectActivity activity = ProjectActivity.record(PROJECT_ID, ActivityType.PROJECT_UPDATED,
                ActorType.AI, ACTOR, "AI applied a suggested change", null, "corr-2");

        assertThat(activity.getActorType()).isEqualTo(ActorType.AI);
    }

    @Test
    @DisplayName("allows the system to act without an actor")
    void allowsSystemActor() {
        assertThatCode(() -> ProjectActivity.recordSystemAction(
                PROJECT_ID, ActivityType.PROJECT_ARCHIVED, "Archived by retention policy", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuses an anonymous human action")
    void refusesAnonymousHumanAction() {
        assertThatThrownBy(() -> ProjectActivity.record(PROJECT_ID, ActivityType.PROJECT_CREATED,
                ActorType.HUMAN, null, "Created", null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("actor identifier is required");
    }

    @Test
    @DisplayName("refuses an anonymous AI action")
    void refusesAnonymousAiAction() {
        assertThatThrownBy(() -> ProjectActivity.record(PROJECT_ID, ActivityType.PROJECT_UPDATED,
                ActorType.AI, null, "Changed", null, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("carries a structured payload when one is supplied")
    void carriesPayload() {
        String payload = "{\"field\":\"name\",\"from\":\"A\",\"to\":\"B\"}";

        ProjectActivity activity = ProjectActivity.record(PROJECT_ID, ActivityType.PROJECT_UPDATED,
                ActorType.HUMAN, ACTOR, "Renamed", payload, null);

        assertThat(activity.getPayload()).contains(payload);
    }

    @Test
    @DisplayName("exposes no mutator, because an editable audit record is not an audit record")
    void exposesNoMutators() {
        boolean hasSetter = java.util.Arrays.stream(ProjectActivity.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("set"));

        assertThat(hasSetter).isFalse();
    }
}
