package com.projectmodule.security;

import com.projectmodule.common.context.AuthenticatedIdentity;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * An email sign-in's bearer token, verified for one request ({@code AccountTokenFilter}). It is never kept in
 * a session: the token is sent with every request, and the account package
 * ({@link com.projectmodule.common.context.BearerTokenAuthenticator}) checks it each time.
 *
 * <p>Workeasy360 says nothing about these sessions: no entitlement check, no back-channel logout. Signing out
 * revokes the token ({@code POST /api/v1/auth/logout}).
 */
public class AccountTokenAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final AuthenticatedIdentity identity;

    public AccountTokenAuthentication(AuthenticatedIdentity identity) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.identity = identity;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedIdentity getPrincipal() {
        return identity;
    }

    @Override
    public String getName() {
        return identity.userId().toString();
    }
}
