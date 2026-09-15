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
     * <p>Requests are not rejected here. Whether identity is required is decided by
     * authorization at the point of use, which keeps unauthenticated access to open endpoints
     * such as health checks possible without special cases.
     */
    public static ImmutableRequestContext anonymous(String correlationId) {
        return new ImmutableRequestContext(
                Optional.empty(), Optional.empty(), correlationId, ActorType.HUMAN);
    }

    public static ImmutableRequestContext anonymous() {
        return anonymous(UUID.randomUUID().toString());
    }
}
