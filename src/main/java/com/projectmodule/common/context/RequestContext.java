package com.projectmodule.common.context;

import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Optional;

/**
 * The identity and tracing information attached to a unit of work.
 *
 * <p>This module does not authenticate. It consumes an identity that an upstream gateway has
 * already authenticated, and exposes it here. Authentication, credential handling and session
 * management all belong to another module.
 *
 * <p>The same abstraction is used by the AI and automation paths, so every layer below sees
 * one uniform notion of "who is asking" regardless of how the work arrived.
 */
public interface RequestContext {

    /** The calling user, absent when the request carried no identity. */
    Optional<ExternalUserId> userId();

    /** The tenant scope, absent when the request carried no organization. */
    Optional<OrganizationId> organizationId();

    /** Always present; generated if the caller did not supply one. */
    String correlationId();

    /** Always present; {@link ActorType#HUMAN} for anything arriving over HTTP. */
    ActorType actorType();

    /**
     * The calling user, required.
     *
     * @throws MissingIdentityException if the request carried no identity
     */
    default ExternalUserId requireUserId() {
        return userId().orElseThrow(() -> new MissingIdentityException(
                "No caller identity was supplied with this request"));
    }
}
