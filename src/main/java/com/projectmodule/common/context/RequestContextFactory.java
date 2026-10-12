package com.projectmodule.common.context;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a {@link RequestContext} from an already-authenticated identity.
 *
 * <p>Kept free of servlet and security types so the rules can be unit tested directly.
 */
public class RequestContextFactory {

    /**
     * @param userId         the authenticated user, or {@code null} when nobody is signed in
     * @param organizationId the organization the session is pinned to, or {@code null}
     * @param correlationId  the request's correlation id; generated when blank
     */
    public RequestContext create(UUID userId, UUID organizationId, String correlationId) {
        // Always HUMAN: actor type is never taken from the caller, so a client cannot present itself as
        // the AI or as an automation run.
        return new ImmutableRequestContext(
                Optional.ofNullable(userId).map(ExternalUserId::of),
                Optional.ofNullable(organizationId).map(OrganizationId::of),
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId,
                ActorType.HUMAN);
    }
}
