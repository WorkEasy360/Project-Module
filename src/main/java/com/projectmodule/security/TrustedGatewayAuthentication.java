package com.projectmodule.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The identity a gateway asserted in {@code X-User-Id} / {@code X-Org-Id}, for one request. Only ever created
 * when the deployment says such a gateway exists ({@code PROJECTMODULE_AUTH_TRUST_IDENTITY_HEADERS=true}, off
 * by default; see {@code docs/project/33-AUTHENTICATION.md}). Either id may be absent, exactly as before: an
 * operation that needs the missing one answers 401.
 */
public class TrustedGatewayAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID organizationId;

    public TrustedGatewayAuthentication(UUID userId, UUID organizationId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
        this.organizationId = organizationId;
        setAuthenticated(true);
    }

    /** May be null. */
    public UUID userId() {
        return userId;
    }

    /** May be null. */
    public UUID organizationId() {
        return organizationId;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
