package com.projectmodule.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestContextFactoryTest {

    private final RequestContextFactory factory = new RequestContextFactory();

    @Test
    @DisplayName("carries the authenticated user and organization")
    void carriesIdentity() {
        UUID user = UUID.randomUUID();
        UUID organization = UUID.randomUUID();

        RequestContext context = factory.create(user, organization, "corr-1");

        assertThat(context.userId()).contains(ExternalUserId.of(user));
        assertThat(context.organizationId()).contains(OrganizationId.of(organization));
        assertThat(context.correlationId()).isEqualTo("corr-1");
        assertThat(context.requireUserId().value()).isEqualTo(user);
    }

    @Test
    @DisplayName("an unauthenticated request has no identity, and asking for one is a 401-class error")
    void anonymous() {
        RequestContext context = factory.create(null, null, null);

        assertThat(context.userId()).isEmpty();
        assertThat(context.organizationId()).isEmpty();
        assertThatThrownBy(context::requireUserId).isInstanceOf(MissingIdentityException.class);
    }

    @Test
    @DisplayName("generates a correlation id when there is none")
    void generatesCorrelationId() {
        String correlationId = factory.create(null, null, "  ").correlationId();

        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("the actor is always HUMAN: a caller cannot present itself as the AI or an automation")
    void actorIsAlwaysHuman() {
        assertThat(factory.create(UUID.randomUUID(), UUID.randomUUID(), "c").actorType()).isEqualTo(ActorType.HUMAN);
    }
}
