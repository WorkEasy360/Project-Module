package com.projectmodule.events.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    private static final UUID AGGREGATE_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static OutboxMessage pending() {
        return OutboxMessage.pending("Project", AGGREGATE_ID, "project.created",
                "{\"id\":\"x\"}", ActorType.HUMAN, ACTOR, "corr-1");
    }

    @Test
    @DisplayName("starts unpublished with no attempts recorded")
    void startsUnpublished() {
        OutboxMessage message = pending();

        assertThat(message.isPublished()).isFalse();
        assertThat(message.getPublishedAt()).isEmpty();
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getLastError()).isEmpty();
        assertThat(message.getSchemaVersion()).isEqualTo(1);
        assertThat(message.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("carries the event name from the module catalogue")
    void carriesEventType() {
        assertThat(pending().getEventType()).isEqualTo("project.created");
    }

    @Test
    @DisplayName("records the actor so an AI-raised event stays distinguishable downstream")
    void recordsActor() {
        OutboxMessage message = pending();

        assertThat(message.getActorType()).contains(ActorType.HUMAN);
        assertThat(message.getActorId()).contains(ACTOR);
    }

    @Test
    @DisplayName("marking published stamps the time and clears the last error")
    void marksPublished() {
        OutboxMessage message = pending();
        message.markFailed("broker refused");

        message.markPublished();

        assertThat(message.isPublished()).isTrue();
        assertThat(message.getPublishedAt()).isPresent();
        assertThat(message.getLastError()).isEmpty();
    }

    @Test
    @DisplayName("counts failures so a stuck event is visible rather than retried silently")
    void countsFailures() {
        OutboxMessage message = pending();

        message.markFailed("timeout");
        message.markFailed("timeout again");

        assertThat(message.getAttemptCount()).isEqualTo(2);
        assertThat(message.getLastError()).contains("timeout again");
        assertThat(message.isPublished()).isFalse();
    }
}
