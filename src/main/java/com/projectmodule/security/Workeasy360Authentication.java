package com.projectmodule.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated state of a session: a {@link Workeasy360Principal}, no credentials.
 *
 * <p>Authorization inside the application is by project membership, not by role, so every signed-in
 * person has the one authority; it exists so Spring Security has something to grant.
 */
public class Workeasy360Authentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final Workeasy360Principal principal;

    public Workeasy360Authentication(Workeasy360Principal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Workeasy360Principal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.subject();
    }
}
