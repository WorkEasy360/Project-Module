package com.projectmodule.common.context;

import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Optional;

/**
 * The identity and tracing information attached to a unit of work.
 *
 * <p>The identity is the request's verified credential, and nothing else: a Workeasy360 sign-in session
 * (the user and organization Workeasy360 vouched for at sign-in) or an email sign-in's bearer token
 * ({@code com.projectmodule.security}; credentials themselves are handled only by the {@code account}
 * and {@code security} packages). Nothing else a request carries - a parameter, a body, an identity
 * header - can change it, unless a deployment has explicitly said a trusted gateway sets the identity
 * headers ({@link RequestContextHeaders}).
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
