package com.projectmodule.common.context;

import java.util.Optional;

/** Resolves a bearer token to its owner. Lives here so the web filter need not know about accounts. */
public interface BearerTokenAuthenticator {

    /** @return the token's owner, or empty if the token is unknown, expired or revoked */
    Optional<AuthenticatedIdentity> authenticate(String token);
}
