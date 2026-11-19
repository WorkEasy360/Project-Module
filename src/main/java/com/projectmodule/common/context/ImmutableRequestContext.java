package com.projectmodule.common.context;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable {@link RequestContext}, built once per unit of work and never mutated afterwards.
 */
public record ImmutableRequestContext(
        Optional<ExternalUserId> userId,
        Optional<OrganizationId> organizationId,
        String correlationId,
        ActorType actorType) implements RequestContext {

    public ImmutableRequestContext {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
    }

    /**
     * A context with no identity, used when a request reaches the module without one.
     *
     * <p>Requests are not rejected here. The security chain decides which paths need a session, and
     * authorization decides at the point of use whether an identity is required.
     */
    public static ImmutableRequestContext anonymous(String correlationId) {
        return new ImmutableRequestContext(
                Optional.empty(), Optional.empty(), correlationId, ActorType.HUMAN);
    }

    public static ImmutableRequestContext anonymous() {
        return anonymous(UUID.randomUUID().toString());
    }
}
