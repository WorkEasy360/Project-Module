package com.projectmodule.common.context;

import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a {@link RequestContext} from the raw header values of an inbound request.
 *
 * <p>Kept free of servlet types so the parsing rules can be unit tested directly and reused if
 * another transport is ever added.
 */
public class RequestContextFactory {

    /**
     * @throws ValidationException if a supplied identifier is not a well-formed UUID
     */
    public RequestContext fromHeaders(String userIdHeader,
                                      String organizationIdHeader,
                                      String correlationIdHeader) {

        Optional<ExternalUserId> userId = hasText(userIdHeader)
                ? Optional.of(ExternalUserId.fromString(userIdHeader.trim()))
                : Optional.empty();

        Optional<OrganizationId> organizationId = hasText(organizationIdHeader)
                ? Optional.of(OrganizationId.fromString(organizationIdHeader.trim()))
                : Optional.empty();

        String correlationId = hasText(correlationIdHeader)
                ? correlationIdHeader.trim()
                : UUID.randomUUID().toString();

        // Always HUMAN: actor type is never taken from a caller-supplied header, so a client
        // cannot present itself as the AI or as an automation run.
        return new ImmutableRequestContext(
                userId, organizationId, correlationId, ActorType.HUMAN);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
