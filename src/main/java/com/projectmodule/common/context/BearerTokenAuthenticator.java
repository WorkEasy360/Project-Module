package com.projectmodule.common.context;

import java.util.Optional;

/** Resolves a bearer token to its owner. Lives here so the web filter need not know about accounts. */
public interface BearerTokenAuthenticator {

    /** The scheme prefix of an {@code Authorization} header that carries a session token. */
    String BEARER_PREFIX = "Bearer ";

    /** @return the token's owner, or empty if the token is unknown, expired or revoked */
    Optional<AuthenticatedIdentity> authenticate(String token);

    /** The token in an {@code Authorization} header value, or null if it does not carry one. */
    static String tokenFrom(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
